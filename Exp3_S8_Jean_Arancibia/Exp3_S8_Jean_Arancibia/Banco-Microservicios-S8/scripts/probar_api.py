"""Prueba la arquitectura S8 realmente levantada: OAuth2, API, JMS y resiliencia."""
import base64,json,os,time,urllib.request,urllib.parse,urllib.error
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
# Reutilizar las mismas credenciales que Docker Compose lee de .env.
if (ROOT/'.env').exists():
    for line in (ROOT/'.env').read_text(encoding='utf-8-sig').splitlines():
        line=line.strip()
        if line and not line.startswith('#') and '=' in line:
            key,value=line.split('=',1);os.environ.setdefault(key,value.strip().strip('"').strip("'"))
BASE=os.environ.get('API_URL','http://localhost:8081')
AUTH=os.environ.get('AUTH_URL','http://localhost:9000')
TOKEN='';results=[]

def request(method,path,body=None,expected=200,auth='token',base=BASE,form=None):
    headers={}
    if auth=='token':headers['Authorization']='Bearer '+TOKEN
    elif auth=='invalid':headers['Authorization']='Bearer invalido'
    elif isinstance(auth,tuple):headers['Authorization']='Basic '+base64.b64encode((':'.join(auth)).encode()).decode()
    data=None
    if body is not None:headers['Content-Type']='application/json';data=json.dumps(body).encode()
    if form is not None:headers['Content-Type']='application/x-www-form-urlencoded';data=urllib.parse.urlencode(form).encode()
    req=urllib.request.Request(base+path,data=data,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=15) as response:code=response.status;raw=response.read().decode()
    except urllib.error.HTTPError as response:code=response.code;raw=response.read().decode()
    try:data=json.loads(raw)
    except json.JSONDecodeError:data=raw
    if isinstance(data,dict):visible={k:('[TOKEN OMITIDO]' if k in {'access_token','refresh_token','id_token'} else v) for k,v in data.items()}
    elif isinstance(data,list):visible={'total_en_respuesta':len(data),'primeros_registros':data[:3]}
    else:visible=data
    if isinstance(visible,str) and len(visible)>1500:visible=visible[:1500]+'... [recortado]'
    results.append({'metodo':method,'url':base+path,'http':code,'esperado':expected,'respuesta':visible})
    print(method,path,code,'OK' if code==expected else 'FALLO',flush=True)
    assert code==expected,(path,code,expected,data)
    return data

def main():
    global TOKEN
    dest=ROOT/'evidencias';dest.mkdir(exist_ok=True)
    infra=(os.environ.get('CLOUD_USER','cloud'),os.environ.get('CLOUD_PASSWORD','CloudLocal2026!'))
    admin=('banco-admin',os.environ.get('OAUTH_ADMIN_SECRET','AdminClientLocal2026!'))
    reader=('banco-lectura',os.environ.get('OAUTH_READER_SECRET','ReaderClientLocal2026!'))
    request('GET','/.well-known/openid-configuration',base=AUTH,auth='none')
    keys=request('GET','/oauth2/jwks',base=AUTH,auth='none');assert all('d' not in k for k in keys['keys'])
    request('POST','/oauth2/token',base=AUTH,auth=('banco-admin','incorrecto'),form={'grant_type':'client_credentials'},expected=401)
    request('POST','/oauth2/token',base=AUTH,auth=reader,form={'grant_type':'client_credentials','scope':'banco.admin'},expected=400)
    token=request('POST','/oauth2/token',base=AUTH,auth=admin,form={'grant_type':'client_credentials','scope':'cuentas.read reportes.write banco.admin'})
    TOKEN=token['access_token']
    request('GET','/actuator/health',auth='none')
    request('GET','/cuentas-service/default',auth='none',base='http://localhost:8888',expected=401)
    config=request('GET','/cuentas-service/default',auth=infra,base='http://localhost:8888')
    assert any('app.config-source' in p['source'] for p in config['propertySources'])
    for _ in range(20):
        credentials=base64.b64encode((':'.join(infra)).encode()).decode()
        req=urllib.request.Request('http://localhost:8761/eureka/apps',headers={'Authorization':'Basic '+credentials,'Accept':'application/json'})
        with urllib.request.urlopen(req,timeout=10) as response:registry=json.load(response)
        apps=registry.get('applications',{}).get('application',[])
        if isinstance(apps,dict):apps=[apps]
        if any(a.get('name')=='CUENTAS-SERVICE' for a in apps):break
        time.sleep(2)
    else:raise AssertionError('CUENTAS-SERVICE no se registró en Eureka')
    results.append({'metodo':'GET','url':'http://localhost:8761/eureka/apps','http':200,'esperado':200,'respuesta':registry})
    request('GET','/api/cuentas',expected=401,auth='none')
    request('GET','/api/cuentas',expected=401,auth='invalid')
    cuentas=request('GET','/api/cuentas');assert len(cuentas)==50
    cuenta=request('GET','/api/cuentas/106');assert cuenta['interesCalculado']==240 and cuenta['saldoFinal']==12240
    request('GET','/api/cuentas/999999',expected=404)
    request('GET','/api/transacciones?limite=5&offset=0')
    request('GET','/api/transacciones/1')
    request('GET','/api/transacciones?limite=0',expected=400)
    request('GET','/api/reportes/anuales?cuentaId=103&limite=5')
    migrated=request('GET','/api/migracion');assert migrated['baseDeDatos']=={'cuentas':50,'transacciones':387,'movimientosAnuales':524}
    request('GET','/api/info')
    report=request('POST','/api/reportes/solicitudes',expected=202)
    assert report['estado']=='PENDIENTE'
    for _ in range(25):
        report=request('GET','/api/reportes/solicitudes/'+report['id'])
        if report['estado']=='COMPLETADO':break
        time.sleep(0.5)
    else:raise AssertionError('El consumidor JMS no completó el reporte')
    assert report['resultado']['cuentas']==50
    admin_token=TOKEN
    reader_token=request('POST','/oauth2/token',base=AUTH,auth=reader,form={'grant_type':'client_credentials','scope':'cuentas.read'})
    TOKEN=reader_token['access_token']
    request('GET','/api/cuentas')
    request('GET','/api/admin/circuito',expected=403)
    request('POST','/api/reportes/solicitudes',expected=403)
    TOKEN=admin_token
    demo=os.environ.get('DEMO_TEST','').lower()=='true' or 'demo' in os.environ.get('APP_PROFILES','').split(',')
    if demo:
        request('POST','/api/demo/fallo',{'activo':True})
        try:
            for _ in range(4):request('GET','/api/cuentas',expected=503)
            assert request('GET','/api/admin/circuito')['estado']=='OPEN'
        finally:request('POST','/api/demo/fallo',{'activo':False})
        time.sleep(3.3)
        for _ in range(2):request('GET','/api/cuentas')
        assert request('GET','/api/admin/circuito')['estado']=='CLOSED'
    from probar_pkce import probar
    pkce=probar()
    print('Authorization Code + PKCE S256: OK',flush=True)
    reporte={'fecha_utc':datetime.now(timezone.utc).isoformat(),'resultado':'TODAS LAS PRUEBAS EJECUTADAS CORRECTAS','circuito_demo':('PROBADO' if demo else 'NO EJECUTADO: activar perfil demo para esta prueba'),'oauth2_pkce':pkce,'peticiones':results}
    (dest/'ejecucion_api_s8.json').write_text(json.dumps(reporte,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    lines=['Ejecución real - Banco XYZ Semana 8','Fecha UTC: '+reporte['fecha_utc'],'','Resultado: '+reporte['resultado'],'Circuito: '+reporte['circuito_demo'],'','OAuth2 PKCE: '+json.dumps(pkce,ensure_ascii=False,indent=2),'']
    for i,r in enumerate(results,1):lines += [str(i)+'. '+r['metodo']+' '+r['url'],'HTTP '+str(r['http'])+' (esperado '+str(r['esperado'])+')',json.dumps(r['respuesta'],ensure_ascii=False,indent=2),'']
    (dest/'ejecucion_api_s8.txt').write_text('\n'.join(lines),encoding='utf-8')
    print('Evidencias HTTP guardadas en evidencias/',flush=True)

if __name__=='__main__':main()
