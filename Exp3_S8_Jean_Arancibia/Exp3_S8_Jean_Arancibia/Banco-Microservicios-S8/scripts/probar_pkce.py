"""Cliente de prueba del flujo Authorization Code + PKCE S256, sin guardar tokens."""
import base64,hashlib,http.cookiejar,json,os,secrets,urllib.request,urllib.parse,urllib.error
from html.parser import HTMLParser
from pathlib import Path
_env_file=Path(__file__).resolve().parents[1]/'.env'
if _env_file.exists():
    for line in _env_file.read_text(encoding='utf-8-sig').splitlines():
        line=line.strip()
        if line and not line.startswith('#') and '=' in line:
            key,value=line.split('=',1)
            os.environ.setdefault(key,value.strip().strip('"').strip("'"))
BASE=os.environ.get('AUTH_URL','http://localhost:9000')
REDIRECT='http://127.0.0.1:8090/callback'

class FormParser(HTMLParser):
    def __init__(self):super().__init__();self.values=[]
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='input' and a.get('name'):
            self.values.append((a['name'],a.get('value',''),a.get('type','text')))

class StopCallback(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        if newurl.startswith(REDIRECT):return None
        return super().redirect_request(req,fp,code,msg,headers,newurl)

def codigo():
    verifier=secrets.token_urlsafe(48)
    challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
    state=secrets.token_urlsafe(24)
    jar=http.cookiejar.CookieJar()
    opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar),StopCallback())
    query={'response_type':'code','client_id':'banco-web','redirect_uri':REDIRECT,'scope':'openid profile cuentas.read','state':state,'code_challenge':challenge,'code_challenge_method':'S256'}
    url=BASE+'/oauth2/authorize?'+urllib.parse.urlencode(query)
    def abrir(req):
        try:
            with opener.open(req,timeout=15) as response:return response.read().decode(),None
        except urllib.error.HTTPError as e:
            location=e.headers.get('Location','')
            if e.code==302 and location.startswith(REDIRECT):return '',location
            raise
    html,callback=abrir(urllib.request.Request(url,headers={'Accept':'text/html'}))
    if not callback:
        parser=FormParser();parser.feed(html)
        csrf=next(v for n,v,t in parser.values if n=='_csrf')
        values={'username':os.environ.get('ADMIN_USER','admin'),'password':os.environ.get('ADMIN_PASSWORD','AdminLocal2026!'),'_csrf':csrf}
        html,callback=abrir(urllib.request.Request(BASE+'/login',data=urllib.parse.urlencode(values).encode(),headers={'Accept':'text/html','Content-Type':'application/x-www-form-urlencoded'}))
    if not callback:
        parser=FormParser();parser.feed(html)
        values=[(n,v) for n,v,t in parser.values if t=='hidden' or n=='scope']
        html,callback=abrir(urllib.request.Request(BASE+'/oauth2/authorize',data=urllib.parse.urlencode(values).encode(),headers={'Accept':'text/html','Content-Type':'application/x-www-form-urlencoded'}))
    assert callback,'El servidor no devolvió un código de autorización'
    returned=urllib.parse.parse_qs(urllib.parse.urlparse(callback).query)
    assert returned.get('state')==[state],'State OAuth2 incorrecto'
    assert 'code' in returned,returned
    return returned['code'][0],verifier

def canjear(code,verifier):
    data=urllib.parse.urlencode({'grant_type':'authorization_code','client_id':'banco-web','redirect_uri':REDIRECT,'code':code,'code_verifier':verifier}).encode()
    req=urllib.request.Request(BASE+'/oauth2/token',data=data,headers={'Content-Type':'application/x-www-form-urlencoded'},method='POST')
    try:
        with urllib.request.urlopen(req,timeout=15) as response:return response.status,json.load(response)
    except urllib.error.HTTPError as response:return response.code,json.load(response)

def probar():
    code,verifier=codigo()
    status,data=canjear(code,verifier)
    assert status==200,(status,data)
    assert data.get('access_token') and data.get('id_token'),data
    req=urllib.request.Request(os.environ.get('API_URL','http://localhost:8081')+'/api/cuentas',headers={'Authorization':'Bearer '+data['access_token']})
    with urllib.request.urlopen(req,timeout=10) as response:
        assert response.status==200
        assert len(json.load(response))==50
    status,reused=canjear(code,verifier)
    assert status==400 and reused.get('error')=='invalid_grant',(status,reused)
    code,verifier=codigo()
    status,wrong=canjear(code,secrets.token_urlsafe(48))
    assert status==400 and wrong.get('error')=='invalid_grant',(status,wrong)
    return {'flujo':'Authorization Code + PKCE S256','login_y_consentimiento':'CORRECTOS','intercambio_codigo':'HTTP 200','api_con_token_usuario':'HTTP 200 - 50 cuentas','reutilizacion_codigo':'HTTP 400 invalid_grant','verificador_incorrecto':'HTTP 400 invalid_grant','tokens':'Omitidos de la evidencia'}

if __name__=='__main__':print(json.dumps(probar(),ensure_ascii=False,indent=2))
