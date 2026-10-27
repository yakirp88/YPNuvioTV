const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const html=fs.readFileSync(__dirname+'/index.html','utf8');
const core=html.split('<script id="probe-core">')[1].split('</script>')[0];
const sandbox={AbortController,TextEncoder,TextDecoder,performance,setTimeout,clearTimeout,fetch};
vm.runInNewContext(core,sandbox);const p=sandbox.GeminiProbe;
const response=(text,finish='STOP')=>JSON.stringify({candidates:[{finishReason:finish,content:{parts:[{text:'DO NOT EXPORT',thought:true},{text}]}}],usageMetadata:{totalTokenCount:4,secret:'DO NOT EXPORT'}});
assert.equal(p.classify(response('','MAX_TOKENS'),'plain_default','nonce').outcome,'output_limit');
assert.equal(p.classify(response(''),'plain_default','nonce').outcome,'empty_visible_output');
assert.equal(p.classify(response('nonce'),'plain_low','nonce').outcome,'verified');
assert.equal(p.classify(response('{"nonce":"nonce","color":"red"}'),'visual_one','nonce').outcome,'verified');
assert.equal(p.classify(response('{"nonce":"nonce","color":"blue"}'),'visual_one','nonce').outcome,'answer_mismatch');
assert(!JSON.stringify(p.classify(response('nonce'),'plain_low','nonce')).includes('DO NOT EXPORT'));
assert.equal(p.payload('visual_24','nonce','testcard').contents[0].parts.length,25);
assert.equal(p.payload('plain_low','nonce').generationConfig.thinkingConfig.thinkingLevel,'low');
assert.equal(p.payload('plain_budget','nonce').generationConfig.thinkingConfig,undefined);
assert.equal(p.payload('structured_default','nonce').generationConfig.thinkingConfig,undefined);
assert.equal(p.payload('plain_budget','nonce').generationConfig.maxOutputTokens,4096);
assert.equal(p.safeHttp('{"error":{"status":"PERMISSION_DENIED","message":"SECRET"}}',403).api_status,'PERMISSION_DENIED');
assert(!JSON.stringify(p.safeHttp('{"error":{"message":"SECRET"}}',400)).includes('SECRET'));
(async()=>{
 const r=await p.probe({key:'SECRET',model:p.MODELS[0],stage:'plain_low',nonce:'nonce',fetchImpl:async(url,opts)=>{
  assert(!url.includes('SECRET'));assert.equal(opts.headers['x-goog-api-key'],'SECRET');assert.equal(opts.redirect,'error');return new Response(response('nonce'));
 }});assert.equal(r.outcome,'verified');assert(!JSON.stringify(r).includes('SECRET'));
 const timeout=await p.probe({key:'SECRET',model:p.MODELS[0],stage:'plain_low',nonce:'nonce',timeoutMs:2,fetchImpl:async(url,opts)=>new Promise((resolve,reject)=>opts.signal.addEventListener('abort',()=>reject(Error('SECRET'))))});
 assert.equal(timeout.outcome,'timeout');assert(!JSON.stringify(timeout).includes('SECRET'));
 const oversized=await p.probe({key:'SECRET',model:p.MODELS[0],stage:'plain_low',nonce:'nonce',fetchImpl:async()=>new Response('x'.repeat(262145))});assert.equal(oversized.outcome,'response_too_large');
 const controller=new AbortController();controller.abort();const cancelled=await p.probe({key:'SECRET',model:p.MODELS[0],stage:'plain_low',nonce:'nonce',signal:controller.signal,fetchImpl:async()=>{throw Error('SECRET');}});assert.equal(cancelled.outcome,'cancelled');
 console.log('Browser core: 14 protocol/privacy/deadline assertions passed');
})().catch(e=>{console.error(e);process.exitCode=1});
