"""Migra los CSV originales; las reglas y el registro de rechazos quedan auditados."""
import argparse, csv, hashlib, json, unicodedata
from datetime import datetime
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def normalizar(value):
    return ''.join(c for c in unicodedata.normalize('NFD', value.strip().lower()) if unicodedata.category(c) != 'Mn')

def fecha(value):
    for formato in ('%Y-%m-%d', '%Y/%m/%d', '%d-%m-%Y', '%d/%m/%Y'):
        try:
            return datetime.strptime(value.strip(), formato).date().isoformat()
        except ValueError:
            pass
    raise ValueError('Fecha inválida')

def positivo(value):
    n = Decimal(value.strip())
    if not n.is_finite() or n <= 0:
        raise ValueError('Monto o saldo debe ser mayor que cero')
    if n != n.quantize(Decimal('0.01')):
        raise ValueError('Monto o saldo tiene más de dos decimales')
    return n.quantize(Decimal('0.01'))

def identidad(value):
    n = int(value.strip())
    if n <= 0:
        raise ValueError('Identificador debe ser positivo')
    return n

def texto(value, campo):
    if not value.strip():
        raise ValueError(campo + ' vacío')
    return value.strip()

def migrar(origen, destino):
    destino.mkdir(parents=True, exist_ok=True)
    rechazados, resumen, fuentes = [], {}, {}
    def procesar(nombre, campos, transformar, clave):
        path = origen / nombre
        raw = path.read_bytes()
        fuentes[nombre] = hashlib.sha256(raw).hexdigest()
        aceptadas, vistos, ids, leidas = [], set(), set(), 0
        with path.open(encoding='utf-8-sig', newline='') as archivo:
            for linea, fila in enumerate(csv.DictReader(archivo), 2):
                leidas += 1
                try:
                    if None in fila or any(v is None for v in fila.values()):
                        raise ValueError('Número de columnas incorrecto')
                    item = transformar(fila)
                    key = clave(item)
                    if key in vistos:
                        raise ValueError('Registro duplicado según clave de negocio')
                    pk = item.get('id', item.get('cuenta_id'))
                    if nombre != 'cuentas_anuales.csv' and pk in ids:
                        raise ValueError('Identificador repetido con datos diferentes; se conserva la primera fila válida')
                    vistos.add(key); ids.add(pk)
                    if nombre == 'cuentas_anuales.csv':
                        item['id'] = len(aceptadas) + 1
                    aceptadas.append(item)
                except (ValueError, KeyError, InvalidOperation) as exc:
                    rechazados.append({'archivo':nombre,'linea':linea,'motivo':str(exc), 'datos':json.dumps(fila, ensure_ascii=False)})
        nombre_salida = {'intereses.csv':'cuentas_migradas.csv', 'transacciones.csv':'transacciones_migradas.csv', 'cuentas_anuales.csv':'cuentas_anuales_migradas.csv'}[nombre]
        with (destino/nombre_salida).open('w', encoding='utf-8', newline='') as archivo:
            w=csv.DictWriter(archivo, fieldnames=campos); w.writeheader(); w.writerows(aceptadas)
        resumen[nombre]={'leidas':leidas, 'aceptadas':len(aceptadas), 'rechazadas':leidas-len(aceptadas)}
    def cuenta(f):
        saldo=positivo(f['saldo']); edad=int(f['edad'].strip()); tipo=normalizar(f['tipo'])
        if not 18 <= edad <= 100: raise ValueError('Edad fuera de rango 18–100')
        if tipo not in ('ahorro','prestamo'): raise ValueError('Tipo de cuenta inválido')
        interes=(saldo*Decimal('0.01' if tipo=='ahorro' else '0.02')).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
        return dict(cuenta_id=identidad(f['cuenta_id']),nombre=texto(f['nombre'],'Nombre'),saldo=saldo,edad=edad,tipo=tipo,interes_calculado=interes,saldo_final=saldo+interes)
    def transaccion(f):
        tipo=normalizar(f['tipo'])
        if tipo not in ('debito','credito'): raise ValueError('Tipo de transacción inválido')
        return dict(id=identidad(f['id']),fecha=fecha(f['fecha']),monto=positivo(f['monto']),tipo=tipo)
    def anual(f):
        tipo=normalizar(f['transaccion'])
        if tipo not in ('deposito','retiro','compra','pago'): raise ValueError('Tipo de operación anual inválido')
        return dict(cuenta_id=identidad(f['cuenta_id']),fecha=fecha(f['fecha']),transaccion=tipo,monto=positivo(f['monto']),descripcion=texto(f['descripcion'],'Descripción'))
    procesar('intereses.csv',['cuenta_id','nombre','saldo','edad','tipo','interes_calculado','saldo_final'],cuenta,lambda x:tuple(x.values()))
    procesar('transacciones.csv',['id','fecha','monto','tipo'],transaccion,lambda x:(x['fecha'],x['monto'],x['tipo']))
    procesar('cuentas_anuales.csv',['id','cuenta_id','fecha','transaccion','monto','descripcion'],anual,lambda x:tuple(x.values()))
    with (destino/'rechazos.csv').open('w',encoding='utf-8',newline='') as archivo:
        w=csv.DictWriter(archivo,fieldnames=['archivo','linea','motivo','datos']); w.writeheader();w.writerows(rechazados)
    lote=hashlib.sha256(json.dumps(fuentes, sort_keys=True).encode()).hexdigest()
    informe={'repositorio':'https://github.com/KariVillagran/bank_legacy_data','carpeta_origen':origen.name,'lote_sha256':lote,'sha256_fuentes':fuentes,'archivos':resumen,'total_leidas':sum(v['leidas'] for v in resumen.values()),'total_aceptadas':sum(v['aceptadas'] for v in resumen.values()),'total_rechazadas':len(rechazados)}
    (destino/'resumen_migracion.json').write_text(json.dumps(informe,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    return informe

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--origen',type=Path,default=ROOT/'datos/originales/semana_3');p.add_argument('--destino',type=Path,default=ROOT/'cuentas-service/src/main/resources/migrados')
    a=p.parse_args();print(json.dumps(migrar(a.origen,a.destino),indent=2,ensure_ascii=False))
