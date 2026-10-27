import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error

spec = importlib.util.spec_from_file_location('check', Path(__file__).with_name('check.py'))
check = importlib.util.module_from_spec(spec); spec.loader.exec_module(check)

class CheckTest(unittest.TestCase):
    def body(self, text='YP_test', finish='STOP', thought=None):
        parts = ([{'text': thought, 'thought': True}] if thought else []) + [{'text': text}]
        return json.dumps({'candidates': [{'finishReason': finish, 'content': {'parts': parts}}],
            'usageMetadata': {'totalTokenCount': 8, 'secret': 'must not appear'}}).encode()

    def test_candidate_and_http200_alone_are_not_success(self):
        self.assertEqual(check.classify_response(self.body('', 'MAX_TOKENS', 'private thought'), 'plain_default', 'YP_test')['outcome'], 'output_limit')
        self.assertEqual(check.classify_response(self.body(''), 'plain_default', 'YP_test')['outcome'], 'empty_visible_output')
        self.assertEqual(check.classify_response(self.body('wrong'), 'plain_default', 'YP_test')['outcome'], 'answer_mismatch')

    def test_nonce_and_schema_and_visual_answer_must_match(self):
        self.assertEqual(check.classify_response(self.body(), 'plain_low', 'YP_test')['outcome'], 'verified')
        self.assertEqual(check.classify_response(self.body('{"nonce":"YP_test"}'), 'structured_low', 'YP_test')['outcome'], 'verified')
        self.assertEqual(check.classify_response(self.body('{"nonce":"YP_test","color":"red"}'), 'visual_one', 'YP_test')['outcome'], 'verified')
        self.assertEqual(check.classify_response(self.body('{"nonce":"YP_test","color":"blue"}'), 'visual_one', 'YP_test')['outcome'], 'answer_mismatch')

    def test_credentials_responses_and_error_messages_are_absent(self):
        body = b'{"error":{"status":"PERMISSION_DENIED","message":"SECRET https://private.test"}}'
        self.assertEqual(check.safe_http(body, 403), {'outcome':'http_error', 'http_status':403, 'api_status':'PERMISSION_DENIED'})
        result = check.classify_response(self.body(thought='SECRET'), 'plain_default', 'YP_test')
        self.assertNotIn('SECRET', json.dumps(result)); self.assertNotIn('secret', json.dumps(result))

    def test_payloads_use_fixed_data_and_low_thinking_separately(self):
        a = check.payload('plain_default', 'nonce'); b = check.payload('plain_low', 'nonce')
        self.assertNotIn('thinkingConfig', a['generationConfig'])
        self.assertNotIn('thinkingConfig', check.payload('plain_budget','nonce')['generationConfig'])
        self.assertNotIn('thinkingConfig', check.payload('structured_default','nonce')['generationConfig'])
        self.assertEqual(check.payload('plain_budget','nonce')['generationConfig']['maxOutputTokens'],4096)
        self.assertEqual(b['generationConfig']['thinkingConfig'], {'thinkingLevel':'low'})
        self.assertEqual(len(check.payload('visual_24','nonce')['contents'][0]['parts']),25)
        self.assertTrue(check.png().startswith(b'\x89PNG'))

    def test_transport_uses_header_not_url_and_never_exports_raw_response(self):
        owner=self
        class Response:
            status=200
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def read(self,n): return owner.body()
        class Opener:
            def open(self,request,timeout):
                owner.assertNotIn('SECRET',request.full_url)
                owner.assertEqual(request.get_header('X-goog-api-key'),'SECRET')
                return Response()
        result=check.perform('SECRET',check.MODELS[0],'plain_low','YP_test',Opener())
        self.assertEqual(result['outcome'],'verified'); self.assertNotIn('SECRET',json.dumps(result))

    def test_redirects_are_refused(self):
        self.assertIsNone(check.NoRedirect().redirect_request(None,None,307,'',{},'https://other.test'))

if __name__ == '__main__': unittest.main()
