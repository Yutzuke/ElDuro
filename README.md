# ElDuro

App Android de finanzas **100 % local** para la economía venezolana: bolívares y dólares, efectivo y digital, tasas BCV y Binance.

> Sin cuentas, sin nube, sin anuncios. Tus datos viven en el teléfono y en CSV legibles en la carpeta que elijas.

## Qué hace

- **Libros separados** (Personal / Tienda), cada uno con sus carteras: Bs efectivo, Bs digital, $ efectivo, $ digital, USDT…
- **Cuatro tipos de movimiento bien separados**: entrada, gasto, transferencia (retiros, cambios de divisa) y ajuste (descuadres). Los saldos iniciales y los cuadres no inflan los ingresos.
- **Entrada rápida**: `-8000 pollo`, `+15$ venta`, `8.000 ef comida`, `20$ > bs 1048 cambio`, `500 bs dig > ef`.
- **Tasas BCV y Binance P2P** consultadas solas (ve.dolarapi.com, bcv.org.ve, API P2P de Binance) pero **nunca obligatorias**: sin internet usa la última conocida marcada como estimada, y corrige los cálculos cuando llega la real. Una tasa manual siempre gana. Histórico BCV para fechas pasadas.
- **Moneda principal seleccionable**: $ BCV, $ Binance o Bs. Cada movimiento conserva su moneda original.
- **Resumen del mes**: entradas, gastos, resultado, descuadres, ganancia/pérdida cambiaria, saldos efectivo × digital y gastos por categoría.
- **Arqueo y conciliación**: cuentas el efectivo o copias el saldo del banco; la diferencia queda registrada.
- **Categorías que aprenden** de tus correcciones.
- **Espejo en CSV** (`carteras.csv`, `tasas.csv`, `<Libro>/<aaaa-mm>.csv`), separador `;` y coma decimal para abrirlos en LibreOffice. Si los editas fuera (p. ej. en el PC vía Syncthing), la app importa los cambios; la versión anterior queda en `.anteriores/`.
- **Importar hojas de cálculo** con secciones BOLÍVARES / DÓLARES (corrige fechas escritas como texto o con día y mes invertidos).

## Compilar

Requiere JDK 17 y Android SDK 34.

```sh
./gradlew assembleRelease      # firma con keystore.properties si existe
./gradlew testReleaseUnitTest
```

Tras instalar, concede «Acceso a todos los archivos» para escribir en la microSD.

## Licencia

GPL-3.0
