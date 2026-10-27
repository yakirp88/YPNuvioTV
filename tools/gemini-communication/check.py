"""Standalone Gemini transport check. Standard library only; never prints/stores credentials."""
import argparse
import base64
import datetime
import getpass
import hashlib
import json
import os
from pathlib import Path
import secrets
import socket
import ssl
import struct
import time
import urllib.error
import urllib.request
import zlib

VERSION = '1.0.0'
MODELS = ('gemini-3.8-flash', 'gemini-3.6-flash')
BASE = 'https://generativelanguage.googleapis.com/v1beta/models/'
LIMIT = 262144
STATUSES = {'INVALID_ARGUMENT', 'PERMISSION_DENIED', 'UNAUTHENTICATED', 'RESOURCE_EXHAUSTED',
            'NOT_FOUND', 'UNAVAILABLE', 'DEADLINE_EXCEEDED', 'INTERNAL', 'FAILED_PRECONDITION'}
REASONS = {'API_KEY_INVALID', 'API_KEY_EXPIRED', 'API_KEY_SERVICE_BLOCKED', 'API_KEY_HTTP_REFERRER_BLOCKED',
           'API_KEY_IP_ADDRESS_BLOCKED', 'API_KEY_ANDROID_APP_BLOCKED', 'SERVICE_DISABLED', 'BILLING_DISABLED'}
FINISHES = {'STOP', 'MAX_TOKENS', 'SAFETY', 'RECITATION', 'BLOCKLIST', 'PROHIBITED_CONTENT', 'OTHER'}

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def png(noise=False):
    """Generated test card only. No screenshot or local media access."""
    w, h = (240, 120) if noise else (32, 32)
    raw = bytearray()
    seed = hashlib.sha256(b'YP Gemini synthetic card v1').digest()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            red = not noise or (w // 4 <= x < 3 * w // 4 and h // 4 <= y < 3 * h // 4)
            if red: raw.extend((255, 0, 0))
            else:
                seed = hashlib.sha256(seed).digest()
                raw.extend(seed[:3])
    def chunk(kind, data):
        return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')


def payload(stage, nonce):
    structured = stage.startswith('structured_') or stage.startswith('visual_')
    visual = stage.startswith('visual_')
    prompt = ('Return only JSON with nonce equal to ' + nonce + (', and color naming the center rectangle color in English.' if visual else '.')
              if structured else 'Reply with exactly ' + nonce + ' and no other text.')
    parts = [{'text': prompt}]
    if visual:
        card = base64.b64encode(png(stage == 'visual_24')).decode('ascii')
        parts += [{'inlineData': {'mimeType': 'image/png', 'data': card}}] * (24 if stage == 'visual_24' else 1)
    config = {'maxOutputTokens': 256 if stage == 'plain_default' else 4096}
    if stage in ('plain_low', 'structured_low', 'visual_one', 'visual_24'): config['thinkingConfig'] = {'thinkingLevel': 'low'}
    if structured:
        props = {'nonce': {'type': 'string'}}
        if visual: props['color'] = {'type': 'string'}
        config['responseFormat'] = {'text': {'mimeType': 'APPLICATION_JSON', 'schema': {
            'type': 'object', 'properties': props, 'required': list(props), 'additionalProperties': False}}}
    return {'contents': [{'role': 'user', 'parts': parts}], 'generationConfig': config}


def classify_response(body, stage, nonce):
    """HTTP 200/candidate presence alone never means successful inference."""
    try:
        doc = json.loads(body)
        if not isinstance(doc, dict): return {'outcome': 'response_invalid'}
        candidates = doc.get('candidates') or []
        if not candidates: return {'outcome': 'no_candidates'}
        first = candidates[0]
        finish = first.get('finishReason', '')
        text = ''.join(p.get('text', '') for p in first.get('content', {}).get('parts', [])
                       if not p.get('thought', False) and isinstance(p.get('text', ''), str))
        result = {'finish': finish if finish in FINISHES else 'UNKNOWN', 'visible_characters': len(text)}
        usage = doc.get('usageMetadata', {})
        result['usage'] = {k: v for k, v in usage.items() if k in {
            'promptTokenCount', 'candidatesTokenCount', 'thoughtsTokenCount', 'totalTokenCount'}
            and type(v) is int and 0 <= v <= 10000000}
        if finish != 'STOP': result['outcome'] = 'output_limit' if finish == 'MAX_TOKENS' else 'incomplete_or_blocked'
        elif not text.strip(): result['outcome'] = 'empty_visible_output'
        else:
            if stage.startswith('plain_'): matched = text.strip() == nonce
            else:
                answer = json.loads(text)
                matched = isinstance(answer, dict) and answer.get('nonce') == nonce
                if stage.startswith('visual_'): matched = matched and str(answer.get('color', '')).strip().lower() == 'red'
            result['outcome'] = 'verified' if matched else 'answer_mismatch'
        return result
    except (ValueError, TypeError, KeyError, AttributeError, IndexError):
        return {'outcome': 'response_invalid'}


def safe_http(body, code):
    result = {'outcome': 'http_error', 'http_status': code}
    try:
        error = json.loads(body).get('error', {})
        status = error.get('status', '')
        if status in STATUSES: result['api_status'] = status
        for detail in error.get('details', []):
            reason = detail.get('reason') if isinstance(detail, dict) else None
            if reason in REASONS: result['api_reason'] = reason; break
    except (ValueError, TypeError, AttributeError): pass
    return result


def perform(key, model, stage, nonce, opener=None, timeout=None):
    assert model in MODELS
    data = json.dumps(payload(stage, nonce), separators=(',', ':')).encode()
    request = urllib.request.Request(BASE + model + ':generateContent', data=data,
        headers={'x-goog-api-key': key, 'Content-Type': 'application/json'}, method='POST')
    start = time.monotonic()
    result = {'model': model, 'stage': stage, 'request_bytes': len(data)}
    try:
        # No key in URL, no redirects, no retries, no third-party destinations.
        client = opener or urllib.request.build_opener(NoRedirect())
        with client.open(request, timeout=timeout or (90 if stage.startswith('visual_') else 35)) as response:
            result['headers_ms'] = round((time.monotonic() - start) * 1000)
            body = response.read(LIMIT + 1)
            result['http_status'] = response.status
            result['response_bytes'] = len(body)
            result.update({'outcome': 'response_too_large'} if len(body) > LIMIT else classify_response(body, stage, nonce))
    except urllib.error.HTTPError as error:
        try: result.update(safe_http(error.read(16384), error.code))
        except Exception: result.update({'outcome': 'http_error', 'http_status': error.code})
    except (TimeoutError, socket.timeout): result['outcome'] = 'timeout'
    except ssl.SSLError: result['outcome'] = 'tls_error'
    except urllib.error.URLError as error:
        result['outcome'] = ('dns_error' if isinstance(error.reason, socket.gaierror) else
            'tls_error' if isinstance(error.reason, ssl.SSLError) else
            'timeout' if isinstance(error.reason, (TimeoutError, socket.timeout)) else 'network_error')
    except OSError: result['outcome'] = 'network_error'
    except Exception: result['outcome'] = 'request_or_response_invalid'
    result['elapsed_ms'] = round((time.monotonic() - start) * 1000)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model', choices=MODELS, action='append', help='Default: compare both fixed Google models')
    parser.add_argument('--large', action='store_true', help='Also send 24 generated test cards per model (extra API usage)')
    parser.add_argument('--output', default='gemini-communication-report.json')
    args = parser.parse_args()
    key = os.environ.get('GEMINI_API_KEY') or getpass.getpass('Gemini API key (hidden, local only): ')
    if not key.strip() or any(c.isspace() for c in key.strip()): parser.error('A nonempty key without internal whitespace is required; it will not be saved.')
    key = key.strip()
    stages = ['plain_default', 'plain_budget', 'plain_low', 'structured_default', 'structured_low', 'visual_one'] + (['visual_24'] if args.large else [])
    models = list(dict.fromkeys(args.model or MODELS))
    print(f'Up to {len(stages)*len(models)} Google API requests. API usage may be billed. Only generated test data is sent.')
    report = {'tool_version': VERSION, 'runtime': 'python', 'started_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'results': []}
    output = Path(args.output)
    try:
        stop = False
        for model in models:
            for stage in stages:
                result = perform(key, model, stage, 'YP_' + secrets.token_hex(6))
                report['results'].append(result)
                output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
                print(model, stage, result['outcome'], result.get('http_status', '-'), str(result['elapsed_ms']) + ' ms')
                if result.get('api_reason') in REASONS or result.get('http_status') in (401, 403, 429): stop = True; break
                if result.get('http_status') == 404: break
            if stop: break
    except KeyboardInterrupt:
        print('Stopped; completed results retained.')
    finally:
        key = ''
    print('Sanitized report:', str(output))
    print('A result from this computer does not establish TV network or app behavior.')

if __name__ == '__main__': main()
