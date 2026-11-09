# ElDuro — plan

App Android de finanzas personales y de la tienda, 100 % local, para el Poco X3 NFC (/e/OS, Android 15).
Hermana de YutzuNote: reutiliza su forma de guardar datos legibles en una carpeta y sus copias ZIP.

## Decisiones tomadas (2026-11-09)

- **Libros separados:** *Personal* y *Tienda*, cada uno con sus propias carteras. Hay una vista conjunta opcional.
- **Moneda principal:** **$ (USD)** por defecto, seleccionable entre $BCV, $Binance y Bs.
- **Tasas:** BCV y Binance P2P son dinámicas pero **nunca bloquean** la app (ver más abajo).
- **Carpeta de datos:** `<microSD>/NodeSync/ElDuro/`. Si esa carpeta se sincroniza con Syncthing, los datos llegan solos al PC.

## Modelo financiero

| Concepto | Regla |
|---|---|
| Cartera | Tiene libro, nombre, moneda (Bs, $ o USDT), forma (efectivo o digital) y saldo inicial |
| Entrada | Dinero nuevo. Suma a "Total entradas" |
| Gasto | Dinero que sale. Suma a "Total gastos". Lleva una categoría |
| Transferencia | De una cartera a otra (retiro, depósito, cambio de divisa, caja fuerte, pago de cuota). **No** es entrada ni gasto. Si hay cambio de divisa, guarda la tasa real |
| Ajuste | Resultado de un arqueo (efectivo) o una conciliación (digital). Se muestra aparte como "descuadre" |
| Saldo inicial | Punto de partida de la cartera. **No** es una entrada |

- **Saldo de una cartera** = inicial + entradas − gastos ± transferencias ± ajustes
- **Resultado del mes** = entradas − gastos
- **Patrimonio** = saldos − deudas
- **Crédito (tarjeta, Cashea):** la compra es un gasto el mismo día y la deuda sube; pagar la cuota es una transferencia hacia la deuda.
- **Comisiones:** son gastos de la categoría "Comisiones".
- **Ganancia o pérdida cambiaria:** se compara la tasa real de un cambio con BCV y Binance de ese día.
- **Devaluación:** pérdida en $ del saldo en Bs entre el inicio y el fin del mes.
- **Montos:** se guardan en la moneda original, en enteros de céntimos. Nunca se guarda un monto convertido.

## Tasas

- **Registro de tasa:** `tasas(fecha, fuente[BCV|BINANCE_COMPRA|BINANCE_VENTA], valor, origen[auto|manual|estimada])`.
- **Prioridad:** manual > auto > estimada (la última conocida).
- **Corrección retroactiva:** cuando llega el valor real, reemplaza a la estimada y los reportes se recalculan.
- **Fuentes en cascada:**
  - BCV: `ve.dolarapi.com/v1/dolares` (oficial) → scraping de `bcv.org.ve` → manual.
  - Binance: API P2P `bapi/c2c/v2/friendly/c2c/adv/search` (USDT/VES, compra y venta) → manual.
  - Brecha, spread y avisos de "Mercado Caliente" (≥15 %) y "Mercado Cachete" (≥19 %).
- **Cuándo se consulta:** al abrir la app si la última consulta tiene más de 3 h, o cuando el usuario lo pide. Sin internet no pasa nada.
- **Comprobado el 2026-11-09:** BCV 897,54 y Binance 1.045–1.050.

## Archivos en la carpeta (fuente legible, sincronización en dos sentidos como en YutzuNote)

```
NodeSync/ElDuro/
  carteras.csv  categorias.csv  tasas.csv
  Personal/2026-11.csv     Tienda/2026-11.csv      ← movimientos del mes
  Personal/deudas.csv      Tienda/faltantes.csv
  Exportados/Balance-Personal-2026-11.xlsx  Reportes/2026-11-Personal.pdf
  Backups/ElDuro-AAAAMMDD.zip
```

- **Columnas del CSV de movimientos:** `id;fecha;tipo;cartera;cartera_destino;monto;monto_destino;tasa;categoria;descripcion;deuda_id`.
- **Formato:** `;` como separador, UTF-8 con BOM y decimales con coma, para que LibreOffice lo abra directo.
- **Ediciones desde el PC:** la app las detecta por hash o fecha de modificación y las importa. Si hay conflicto, gana el cambio más reciente y se guarda una copia `(conflicto)`.

## Fases

### Fase 1 — Base
- Libros y carteras con saldo inicial.
- Movimientos de los 4 tipos, con **entrada rápida** (`-8000 pollo`, `+15$ placas`, `20$>bs 1048`).
- Categorías que se aprenden por descripción.
- Tasas con respaldo y la vista en la moneda seleccionable.
- Pantalla del mes: entradas, gastos, resultado y la tabla 2×2 de efectivo/digital.
- Arqueo y conciliación.
- CSV en NodeSync con sincronización.
- Importar `Balance.xlsx`, corrigiendo las fechas escritas como texto y convirtiendo "inicio de caja" en saldo inicial y "cuadre" en ajuste.

### Fase 2 — Control
- Deudas (debo / me deben) con abonos y cuotas: tarjetas de crédito, compras a cuotas, préstamos personales.
- Recurrentes y presupuestos por categoría con aviso al 80 %.
- Faltantes de la tienda que se convierten en gasto.
- Cierre de mes, comparativa y devaluación.
- Exportar a XLSX con el formato de la hoja actual y a PDF.
- ZIP diario.

### Fase 3 — Comodidad
- Widget de saldo y tasas, y calculadora Bs↔$.
- PIN o huella.
- Gráficos.
- Alerta de brecha Binance/BCV.
- Lectura opcional de las notificaciones del banco (local) para proponer movimientos.

## Técnica

- Kotlin, Jetpack Compose y Room.
- minSdk 26, targetSdk 35. Paquete `app.elduro`.
- Compilar con el JDK 17 de `~/.jdks`.
- Firma propia en `keystore/` (fuera de git).
- Repositorio público opcional `Yutzuke/ElDuro`.
