import csv, json, tempfile, unittest
from pathlib import Path
from migrar_datos import migrar, fecha, positivo, ROOT

class MigracionTest(unittest.TestCase):
    def test_fuentes_se_conservan_y_resultado_es_reproducible(self):
        with tempfile.TemporaryDirectory() as temp:
            destino=Path(temp)
            resumen=migrar(ROOT/'datos/originales/semana_3',destino)
            self.assertEqual(resumen['total_leidas'],3000)
            self.assertEqual(resumen['total_aceptadas']+resumen['total_rechazadas'],3000)
            original=ROOT/'cuentas-service/src/main/resources/migrados'
            for p in destino.iterdir():
                self.assertEqual(p.read_bytes(),(original/p.name).read_bytes(),p.name)
            with (destino/'cuentas_migradas.csv').open() as f:
                cuentas=list(csv.DictReader(f))
            self.assertEqual(len({c['cuenta_id'] for c in cuentas}),len(cuentas))
            cuenta=next(c for c in cuentas if c['cuenta_id']=='106')
            self.assertEqual(cuenta['interes_calculado'],'240.00')
            self.assertEqual(cuenta['saldo_final'],'12240.00')
    def test_fechas_imposibles_y_montos_invalidos_se_rechazan(self):
        for f in ['2024-13-01','31/02/2024','sin-fecha']:
            with self.assertRaises(ValueError):fecha(f)
        self.assertEqual(fecha('03-04-2024'),'2024-04-03')
        for monto in ['0','-100','NaN','Infinity','1.001']:
            with self.assertRaises(ValueError):positivo(monto)

if __name__=='__main__': unittest.main()
