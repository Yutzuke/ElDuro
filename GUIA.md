# ElDuro — Manual de uso y guía de construcción

Versión 0.3.0 · noviembre 2026

ElDuro es una app Android de finanzas **100 % local** pensada para la economía venezolana: bolívares y dólares, efectivo y dinero digital, tasas BCV y Binance. No tiene cuentas, nube ni anuncios. Tus datos viven en el teléfono y en archivos CSV legibles en la carpeta que elijas.

**Contenido**

- [Parte 1 · Manual de uso](#parte-1--manual-de-uso)
  1. [Instalación y primer arranque](#1-instalación-y-primer-arranque)
  2. [Conceptos básicos](#2-conceptos-básicos)
  3. [Pantalla Mes](#3-pantalla-mes)
  4. [Anotar movimientos](#4-anotar-movimientos)
  5. [Carteras, arqueo y conciliación](#5-carteras-arqueo-y-conciliación)
  6. [Control: deudas, faltantes, recurrentes y presupuestos](#6-control-deudas-faltantes-recurrentes-y-presupuestos)
  7. [Tasas](#7-tasas)
  8. [Ajustes, libros e importación](#8-ajustes-libros-e-importación)
  9. [Tus archivos en la carpeta](#9-tus-archivos-en-la-carpeta)
  10. [Avisos y trabajo en segundo plano](#10-avisos-y-trabajo-en-segundo-plano)
  11. [Preguntas frecuentes](#11-preguntas-frecuentes)
- [Parte 2 · Guía de construcción](#parte-2--guía-de-construcción)
  1. [Requisitos](#1-requisitos)
  2. [Obtener el código y firmar](#2-obtener-el-código-y-firmar)
  3. [Compilar, probar e instalar](#3-compilar-probar-e-instalar)
  4. [Estructura del proyecto](#4-estructura-del-proyecto)
  5. [Modelo de datos](#5-modelo-de-datos)
  6. [Reglas financieras](#6-reglas-financieras)
  7. [Formato de los CSV](#7-formato-de-los-csv)
  8. [Fuentes de tasas](#8-fuentes-de-tasas)
  9. [Cómo extender la app](#9-cómo-extender-la-app)
  10. [Consumo de recursos](#10-consumo-de-recursos)

---

# Parte 1 · Manual de uso

## 1. Instalación y primer arranque

1. Instala el APK (`ElDuro-x.y.z.apk`). Si Android lo pide, permite instalar desde tu gestor de archivos.
2. Al abrirla por primera vez, la app pide **permiso de notificaciones**. Acéptalo para recibir los avisos de cuotas y presupuestos.
3. En la pantalla Mes aparece la tarjeta **«Falta el acceso a archivos»**. Toca **Conceder acceso** y activa «Permitir acceso para administrar todos los archivos». Sin este permiso la app funciona, pero no guarda los CSV en la carpeta.
4. ElDuro busca sola una carpeta `NodeSync` en la microSD o en la memoria interna, y guarda allí sus datos en `NodeSync/ElDuro/`. Si no la encuentra, usa `ElDuro/` en la memoria interna. Puedes cambiarla en **Ajustes → Carpeta de datos**.

La primera vez la app crea dos libros con estas carteras:

| Libro | Carteras |
|---|---|
| Personal | Bs efectivo, Bs digital, $ efectivo, $ digital, USDT Binance |
| Tienda | Bs efectivo, Bs digital, $ efectivo, $ digital |

> Si reinstalas la app y la carpeta ya tiene CSV de ElDuro, la app **los importa al arrancar**: así recuperas tus datos.

## 2. Conceptos básicos

### Libros
Un libro es una contabilidad separada, por ejemplo *Personal* y *Tienda*. Cada libro tiene sus propias carteras, deudas, faltantes, recurrentes y presupuestos. El libro activo se elige arriba. Con más de 3 libros, el selector se convierte en un menú desplegable.

### Carteras (o billeteras)
Una cartera es un lugar donde hay dinero. Cada una tiene un nombre, una **moneda** (Bs, $ o USDT) y una **forma**:
- **Efectivo:** dinero físico que se puede contar.
- **Digital:** dinero electrónico, como banco, pago móvil, Binance o Zinli.

### Los cinco tipos de movimiento

| Tipo | Icono | Ejemplo | ¿Cuenta en entradas o gastos? |
|---|---|---|---|
| **Entrada** | ↓ | Venta de placas +15 $ | ✅ Suma a **entradas** |
| **Gasto** | ↑ | Compra de pollo −8.000 Bs | ✅ Suma a **gastos** |
| **Transferencia** | ⇄ | Sacar efectivo, cambiar $ a Bs, pagar una cuota | ❌ El dinero solo cambia de lugar |
| **Ajuste** | ≠ | Arqueo: faltan 500 Bs | Aparte, como **descuadre** |
| **Apertura** | ◎ | Saldo inicial de una cartera | ❌ Es el punto de partida |

Esta separación es la clave de ElDuro. El saldo inicial y los "cuadres" **no inflan tus ingresos**, y cambiar dólares **no es un gasto**.

### Fórmulas

- **Saldo de una cartera** = apertura + entradas − gastos ± transferencias ± ajustes
- **Resultado del mes** = entradas − gastos
- **Saldo total** = suma de todas las carteras, convertida a la moneda principal

## 3. Pantalla Mes

De arriba abajo:

1. **Tarjeta de tasas**
   - Muestra BCV, Binance y la **brecha** entre las dos.
   - Con brecha de 15 % o más dice 🔥 *Mercado Caliente*; con 19 % o más, *Mercado Cachete*.
   - El botón ⟳ consulta las tasas en el momento.
   - «·est.» significa tasa estimada (ver [Tasas](#7-tasas)).
2. **Pendientes:** solo aparece si hay algo que atender.
   - Cuotas que vencen en 7 días o menos, con el botón **Pagar**.
   - Recurrentes que esperan confirmación, con **Registrar** o **Saltar**.
3. **Selector de mes:** las flechas ‹ › cambian de mes.
4. **Moneda de la vista:** $ BCV, $ Binance o Bs. Cambia cómo se muestran los totales, nunca los montos originales.
5. **Resumen del mes:**
   - Entradas, gastos y resultado. Si el resultado es negativo, avisa con ⚠ «Gastaste más de lo que entró».
   - **Descuadres** de los arqueos.
   - **Ganancia o pérdida en cambios:** compara la tasa a la que cambiaste con la de referencia.
   - **Devaluación de tus Bs:** cuánto valor en $ perdieron en el mes los bolívares que tenías guardados (aproximado).
   - Compras a crédito incluidas en los gastos.
   - Comparación con el mes anterior.
   - 🟡 Cuántas conversiones usaron tasa estimada.
6. **Saldos al cierre:** tabla de moneda × efectivo/digital y total convertido.
7. **Gastos por categoría:** barras con porcentaje. Si la categoría tiene presupuesto, muestra cuánto del tope llevas.
8. **Exportar:** botones **Excel** y **PDF** del mes y libro actuales (ver [Exportar](#exportar)).
9. **Movimientos:** agrupados por día. Toca uno para **editarlo o borrarlo**. Al borrar puedes **Deshacer**.

## 4. Anotar movimientos

Toca el botón **+**. Escribe en **«Escribe rápido»**; la tarjeta de abajo muestra cómo lo entendió la app. Luego puedes ajustar cualquier campo y tocar **Guardar**.

### Entrada rápida

| Escribes | Resultado |
|---|---|
| `-8000 pollo` | Gasto de 8.000 en la cartera elegida, categoría *Comida* |
| `8000 pollo` | Lo mismo: sin signo se entiende como gasto |
| `+15$ venta placas` | Entrada de 15 $ |
| `8.000 ef comida` | Gasto en **efectivo** |
| `-85 usdt perdida trading` | Gasto en la cartera USDT |
| `20$ > bs 1048 cambio` | Transferencia de 20 $ a Bs, a tasa 1.048. Llegan 20.960 Bs |
| `500 bs dig > ef` | Retiro: de Bs digital a Bs efectivo |
| `100$ ef > dig` | Depósito de $ efectivo a $ digital |

**Reglas de la entrada rápida:**
- **Signo:** `+` significa entrada; `-` o ningún signo, gasto; `>` significa transferencia.
- **Monedas:** `$`, `usd`, `dolar`; `bs`, `ves`, `bolivares`; `usdt`.
- **Formas:** `ef`, `efe`, `efectivo`, `cash` para efectivo; `dig`, `digital`, `pm`, `banco` para digital.
- **Transferencias:** lo que va después de `>` es el destino, y un número allí es la **tasa** (Bs por $).
  - Sin tasa, se propone la de Binance del día.
  - Sin destino explícito: si es la misma moneda, va a la otra forma (retiro o depósito); si es otra moneda, va a la misma forma.
- **Números:** se aceptan en formato venezolano: `8.000` (miles), `8.000,50`, `15,5` o `15.5`.

### Otros campos

- **Tipo:** Gasto, Entrada o Transferencia.
- **Fecha:** toca el botón de fecha para elegir otra.
- **Cartera:** la última que uses queda como predeterminada para la entrada rápida de ese libro.
- **Categoría:** se adivina por la descripción, y **la app aprende** cada vez que la corriges.

**Categorías de gasto:** Comida, Transporte, Hogar, Servicios, Salud, Tienda, Compras, Trading, Comisiones, Familia, Otros.

**Categorías de entrada:** Ventas, Servicio técnico, Sueldo, Trading, Préstamo recibido, Otros ingresos.

> Las **comisiones** bancarias o del P2P anótalas como gasto de la categoría *Comisiones* para saber cuánto pierdes en ellas cada mes.

## 5. Carteras, arqueo y conciliación

En la pestaña **Carteras** ves el saldo de cada cartera del libro y el total.

- **Nueva cartera:** nombre (por ejemplo *Caja fuerte*, *Banesco*, *Zinli*), moneda, forma y **saldo actual**. El saldo actual se registra como apertura, no como ingreso.
- **Editar:** cambia el nombre o la forma. La moneda no se puede cambiar, para no descuadrar el historial.
- **Archivar:** oculta la cartera y conserva su historial. Para verla de nuevo, usa «Ver archivadas».

**Arqueo (carteras de efectivo):** cuentas los billetes y escribes lo que hay. Si no coincide con lo que dice ElDuro, la diferencia se registra como **ajuste** («Arqueo: faltan 500 Bs»).

**Conciliar (carteras digitales):** copias el saldo que muestra el banco o Binance. Funciona igual que el arqueo, y te ayuda a encontrar un pago móvil o una comisión que olvidaste anotar.

## 6. Control: deudas, faltantes, recurrentes y presupuestos

### Deudas

Arriba se ve el total de lo que **debes** y de lo que **te deben**, en la moneda de la vista.

**Nueva deuda:**
- **Sentido:** *Debo* o *Me deben*.
- **Nombre:** por ejemplo *Tarjeta de crédito*, *Cashea* o el nombre de una persona.
- **Moneda, monto total y fecha.**
- **Plan de cuotas (opcional):** número de cuotas, fecha de la primera y frecuencia (semanal, cada 14 días o mensual). Con él, la app calcula la próxima fecha y te avisa.
- **Compra a crédito** (solo en *Debo*): si la marcas, el monto cuenta como **gasto del mes en que compraste**, con su categoría. Los pagos posteriores son transferencias, así que el gasto no se cuenta dos veces.
- **Presté ese dinero ahora** (solo en *Me deben*): registra la salida desde una cartera. Es una transferencia, no un gasto.

**Pagar o cobrar:**
- Elige la cartera. El monto propuesto es la cuota, o el saldo si no hay plan de cuotas.
- Si la cartera tiene **otra moneda** que la deuda, escribe la tasa y la app calcula cuánto se descuenta de la deuda.
- No cuenta como gasto: es una transferencia hacia la deuda.
- Cuando el saldo llega a 0, la deuda pasa a **saldada**. Las saldadas se ven con «Ver saldadas».

**Editar:** cambia los datos, marca la deuda como cerrada o bórrala. Al borrarla, los pagos ya hechos se conservan como transferencias sueltas.

### Faltantes

Es la lista de cosas por comprar, por ejemplo para la tienda.
- Escribe el artículo y toca **+**.
- Al **marcarlo** como comprado se abre el gasto ya rellenado, con la descripción y la categoría *Tienda*. Si cancelas, el artículo vuelve a quedar pendiente.
- **🗑** quita un artículo; **«Quitar los comprados»** limpia la lista.

### Recurrentes

Son movimientos que se repiten: internet, alquiler, sueldo, suscripciones.
- **Datos:** tipo, descripción, monto, cartera, categoría, frecuencia (semanal, cada 14 días o mensual) y próxima fecha.
- **Automático:** se registra solo cuando llega la fecha.
- **No automático:** el día que toca aparece en *Pendientes* de la pantalla Mes, con **Registrar** o **Saltar**.
- No se duplica: registrar dos veces el mismo vencimiento crea un solo movimiento.

### Presupuestos

- Toca una categoría para fijarle un **tope mensual** en $ (comparado al BCV) o en Bs.
- La barra se pone amarilla al **80 %** y roja al **100 %**, y recibes una notificación en cada nivel.

## 7. Tasas

ElDuro usa dos tasas, siempre en **Bs por dólar**:

| Fuente | De dónde sale |
|---|---|
| **BCV** | ve.dolarapi.com (oficial); si falla, la web del BCV |
| **Binance** | API P2P de Binance (USDT/VES), mediana de los 5 primeros anuncios de compra |

**Nunca dependes de internet:**
- Se consultan al abrir la app si pasaron más de 3 horas, cada ~6 horas en segundo plano, o cuando tocas ⟳.
- Si no hay tasa de un día (sin conexión, fin de semana o feriado), se usa la **última conocida** marcada como **estimada** (🟡 / «·est.»). Cuando llega la real, los cálculos **se corrigen solos**.
- Una **tasa manual** (✏️) siempre tiene prioridad sobre la automática.

**En la pestaña Tasas:**
- **Anotar tasa:** fuente, fecha y valor. Si tocas una fila de la tabla, la editas, y puedes quitar una manual para volver a la automática.
- **Rellenar histórico:** descarga las tasas BCV desde tu primer movimiento. Para fechas pasadas de Binance usa el dólar *paralelo* como aproximación, marcado 🟡.
- **Tabla de los últimos 60 días:** BCV, Binance y brecha.

**Qué tasa se usa en cada vista:**
- **$ BCV:** los Bs se dividen por la tasa BCV de la fecha de cada movimiento.
- **$ Binance:** igual, con la tasa Binance.
- **Bs:** los $ se multiplican por la tasa BCV y los USDT por la de Binance.
- El $ y el USDT se consideran 1:1 entre sí.

## 8. Ajustes, libros e importación

### Moneda principal
Elige $ BCV, $ Binance o Bs. Es la misma opción que la de la pantalla Mes.

### Libros
- **Nuevo libro:** se crea con Bs efectivo, Bs digital, $ efectivo y $ digital.
- **Renombrar:** cambia el nombre en todos sus datos y **mueve su carpeta de CSV**. El nombre no puede llevar `/ \ : * ? " < > |` ni empezar por un punto.
- **Archivar / Mostrar:** oculta el libro sin borrar nada. Siempre debe quedar al menos uno visible.
- **↑:** sube el libro en el orden.
- **✕ Borrar:** solo se permite si el libro **no tiene movimientos ni deudas**. Si los tiene, archívalo.

### Carpeta de datos
- **Cambiar:** elige otra carpeta. La app vuelve a sincronizar con ella.
- **Sincronizar ahora:** importa los cambios hechos fuera de la app y escribe todo.

### Importar Balance.xlsx
Lee hojas de cálculo con este formato:
- Por mes, una sección **BOLIVARES** y otra **DOLARES**, cada una con las columnas FECHA | DESCRIPCION | EFECTIVO | DIGITAL y terminada en una fila **TOTAL:**.
- Una hoja **Deudas**, con las mismas secciones y el monto en la columna C.
- Una hoja **FALTANTE…**, con una lista bajo «PENDIENTE POR COMPRAR».

**Al importar:**
- **«Inicio de caja»** pasa a ser **apertura**, y **«cuadre»** pasa a ser **ajuste**.
- **Fechas:** entiende el número de serie de Excel, texto como `14/10/2026`, e incluso errores como `13/102026`.
- **Fechas fuera del mes de su hoja:** si intercambiando día y mes caen en ese mes (10/11 ↔ 11/10), se corrigen así; si no, pasan al día 1 del mes. La vista previa muestra cada corrección.
- **Selección:** eliges qué hojas importar y en qué libro. La hoja *Formato* (plantilla) viene desmarcada.
- **Importar dos veces no duplica nada.**
- Si hay internet, se descargan las **tasas históricas** de esas fechas.

## 9. Tus archivos en la carpeta

```
NodeSync/ElDuro/
├── carteras.csv          carteras de todos los libros
├── tasas.csv             tasas guardadas (auto, manual, paralelo)
├── deudas.csv
├── recurrentes.csv
├── presupuestos.csv
├── faltantes.csv
├── Personal/2026-10.csv  movimientos: un archivo por libro y mes
├── Tienda/2026-11.csv
├── Exportados/           Balance-<Libro>-<aaaa-mm>.xlsx
├── Reportes/             <aaaa-mm>-<Libro>.pdf
├── Backups/              ElDuro-<aaaammdd>.zip (las 30 más recientes)
└── .anteriores/          versión previa de un mes antes de aplicar un CSV editado fuera
```

- **Formato:** los CSV usan `;` como separador, coma decimal y UTF-8, así que se abren directamente en LibreOffice, OnlyOffice o Excel.
- **Editar desde el PC:** si cambias un CSV (por ejemplo, en el PC con Syncthing), al abrir la app **se importa** y ese archivo pasa a ser la verdad para su libro y mes. Lo que había antes se guarda en `.anteriores/`.
- **Archivos borrados:** si borras un CSV, la app lo vuelve a escribir. **Nunca borra datos** porque falte un archivo.
- **Filas nuevas:** puedes añadirlas en el PC dejando la columna `id` vacía; la app les asigna una.

### Exportar
- **Excel:** mismo formato de tu hoja (BOLÍVARES y DÓLARES, columnas EFECTIVO y DIGITAL, filas TOTAL, Gastos y Entradas con fórmulas). Incluye además las hojas **Resumen** (en $ BCV y $ Binance), **Deudas** y **Faltantes**.
- **PDF:** resumen, comparación con el mes anterior, barras por categoría, saldos, deudas pendientes y lista de movimientos.
- Tras exportar se abre el menú de **compartir** (WhatsApp, Telegram, correo…).

### Restaurar
- **En el mismo teléfono o en otro:** instala ElDuro, concede el acceso a archivos y apunta a la misma carpeta. Los CSV se importan solos.
- **Desde una copia:** descomprime un ZIP de `Backups/` en la carpeta y abre la app.

## 10. Avisos y trabajo en segundo plano

Cada ~6 horas (Android puede retrasarlo para ahorrar batería), el vigilante:
- Actualiza las tasas si pasaron más de 3 horas.
- Registra los recurrentes automáticos vencidos.
- Sincroniza la carpeta.
- Hace la copia ZIP del día, si aún no existe.
- Avisa de:
  - Cuotas que vencen en 2 días o menos, o vencidas (un aviso por vencimiento).
  - Recurrentes por confirmar (uno al día).
  - Presupuestos al 80 % y al 100 % (uno por nivel y mes).

## 11. Preguntas frecuentes

**¿Por qué mis «cuadres» no aparecen como ingreso?**
Porque no lo son: son diferencias entre lo anotado y lo real. Aparecen como *descuadres*, para que veas cuánto se te «pierde».

**Pagué la tarjeta y no aparece en gastos. ¿Está bien?**
Sí. El gasto fue la compra; el pago solo mueve dinero hacia la deuda. Si la compra fue a crédito, marca «Compra a crédito» en la deuda para que cuente en el mes en que compraste.

**Veo «·est.» o 🟡 en algunos montos.**
Ese día no había tasa y se usó la última conocida. Toca ⟳ en Tasas o usa «Rellenar histórico».

**«N movimientos sin tasa para convertir».**
No hay ninguna tasa guardada de esa fuente. Actualiza con internet o anota una manual.

**No se crean los CSV.**
Falta el permiso «Acceso a todos los archivos» (Ajustes → Carpeta de datos → Conceder acceso).

**No llegan los avisos.**
Revisa que ElDuro tenga permiso de notificaciones y que no esté restringida en el ahorro de batería.

**Cambié la tarjeta microSD.**
Ve a Ajustes → Carpeta de datos → Cambiar, y elige la nueva ubicación de `ElDuro`.

---

# Parte 2 · Guía de construcción

## 1. Requisitos

| Herramienta | Versión |
|---|---|
| JDK | **17** (con JDK 11 o con 21+ falla AGP 8.5) |
| Android SDK | Platform **34**, Build-tools 34 |
| Gradle | 8.9 (wrapper incluido) |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.24 (Compose compiler 1.5.14) |
| Dispositivo | Android 8.0 o superior (minSdk 26); probado en Android 15 |

Dependencias principales: Jetpack Compose (BOM 2024.06.00, Material 3), Room 2.6.1 con KSP, WorkManager 2.9.0, Coroutines 1.8.1. No usa librerías de terceros para Excel ni para HTTP: el `.xlsx` se lee y escribe a mano (ZIP + XML), y las consultas usan `HttpURLConnection`.

## 2. Obtener el código y firmar

```sh
git clone https://github.com/Yutzuke/ElDuro.git
cd ElDuro
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

La versión *release* se firma con la clave indicada en `keystore.properties`. Ese archivo y la carpeta `keystore/` están en `.gitignore`. Para crear una clave:

```sh
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/elduro.jks -alias elduro \
  -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=ElDuro"
```

```properties
# keystore.properties
storeFile=../keystore/elduro.jks
storePassword=TU_CLAVE
keyAlias=elduro
keyPassword=TU_CLAVE
```

> ⚠️ **Guarda la clave y su contraseña.** Sin ellas no podrás actualizar la app instalada: tendrías que desinstalarla, y perderías la base de datos interna (los CSV de la carpeta se conservan).

Sin `keystore.properties`, el *release* queda sin firmar. El *debug* se instala aparte con el sufijo `.debug`.

## 3. Compilar, probar e instalar

```sh
export JAVA_HOME=/ruta/a/jdk-17
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew testReleaseUnitTest      # pruebas de lógica (JVM)
./gradlew assembleDebug            # versión de depuración (app.elduro.debug)
```

Instalar por ADB y conceder el acceso a archivos sin pasar por la pantalla de ajustes:

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell appops set app.elduro MANAGE_EXTERNAL_STORAGE allow
```

**Pruebas incluidas** (`app/src/test`):
- `LogicaTest`: montos en formato venezolano, entrada rápida, fechas de Excel y prioridad de tasas.
- `ControlTest`: cuotas, pagos en otra moneda, préstamos y vencimientos mensuales.

## 4. Estructura del proyecto

```
app/src/main/java/app/elduro/
├── data/
│   ├── Model.kt          entidades Room: Cartera, Movimiento, Tasa, Aprendizaje,
│   │                     Deuda, Recurrente, Presupuesto, Faltante
│   └── Db.kt             DAOs, convertidores, base de datos (v2, AutoMigration 1→2)
├── core/
│   ├── Dinero.kt         formato y lectura de montos, Vista (moneda principal), fechas
│   ├── Tasas.kt          TablaTasas (búsqueda con respaldo y conversión) y ConsultaTasas (HTTP)
│   ├── EntradaRapida.kt  intérprete de "-8000 pollo", "20$ > bs 1048"
│   ├── Categorias.kt     categorías, palabras clave y aprendizaje
│   ├── Finanzas.kt       saldos y resumen del mes (entradas, gastos, cambiaria, devaluación)
│   ├── Control.kt        estado de deudas y cuotas, abonos, recurrentes, presupuestos
│   ├── Duro.kt           Prefs, operaciones de escritura, sembrado inicial, Libros
│   ├── CarpetaSync.kt    espejo CSV bidireccional con la carpeta
│   ├── ImportarExcel.kt  lector de .xlsx (movimientos, deudas, faltantes)
│   ├── Exportar.kt       escritor de .xlsx, reporte PDF, copias ZIP
│   └── Vigilante.kt      Worker periódico y notificaciones
└── ui/
    ├── MainActivity.kt   EstadoApp (estado compartido), navegación, ciclo de vida
    ├── Tema.kt           colores (verde y oro), modo claro y oscuro
    ├── PantallaMes.kt    resumen, pendientes, categorías, movimientos, exportar
    ├── Editor.kt         hoja de nuevo o editar movimiento, selectores
    ├── PantallaCarteras.kt
    ├── PantallaControl.kt  deudas, faltantes, recurrentes, presupuestos
    ├── PantallaTasas.kt
    └── PantallaAjustes.kt  moneda, libros, carpeta, importar
```

**Flujo de datos:**
- La interfaz observa las tablas con `Flow` (`collectAsState`) y recalcula los resúmenes con `remember`.
- Las escrituras van a `Dispatchers.IO` mediante `EstadoApp.io()`.
- Un `InvalidationTracker` de Room programa la exportación a CSV ~1,5 s después de cada cambio.

## 5. Modelo de datos

**Montos:**
- Son `Long` en **céntimos**, con signo según su efecto en la cartera.
- `Movimiento.monto` afecta a `carteraId`.
- En una transferencia, `montoDestino` (positivo) afecta a `carteraDestinoId`.
- En un abono a deuda (`deudaId != null`), `montoDestino` es lo que se descuenta de la deuda, en la moneda de la deuda.

**Fechas:** `String` ISO `aaaa-mm-dd`.

**Identificadores:**
- Cada movimiento, deuda, recurrente y faltante tiene un `uid` estable que se usa en los CSV.
- Las importaciones de Excel usan uids **deterministas**: `xl…` para movimientos, `xd…` para deudas, `xf…` para faltantes. Los recurrentes usan `rc…`. Así nada se duplica.

**Libros:** no son una tabla. Salen de las carteras más el orden y los archivados guardados en preferencias (`libros_orden`, `libros_archivados`).

**Migraciones:**
- El esquema se exporta a `app/schemas/`.
- Al cambiar una entidad, sube `version` en `@Database` y añade `AutoMigration(from = n, to = n + 1)`. Para renombrar o borrar columnas, añade una `AutoMigrationSpec`.
- **Nunca uses `fallbackToDestructiveMigration`:** borraría los datos del usuario.

## 6. Reglas financieras

- Solo **ENTRADA** y **GASTO** suman a los totales del mes.
- **TRANSFERENCIA**, **AJUSTE** y **APERTURA** cambian saldos, pero no el resultado.
- **Compra a crédito** (`Deuda.esGasto`): su `total` cuenta como gasto en el mes de `fecha`, con su categoría. Los abonos son transferencias.
- **Ganancia o pérdida cambiaria:** en transferencias entre monedas dentro del libro, es el valor de lo que entra menos el valor de lo que sale, ambos convertidos a la vista con la tasa del día.
- **Devaluación:** el saldo en Bs al cierre del mes anterior, convertido con la tasa de ese día y con la del cierre (u hoy); la diferencia es la pérdida. Es aproximada: ignora los movimientos dentro del mes.
- **Búsqueda de tasa:**
  - Prioridad para la misma fecha: manual > auto > paralelo.
  - Si falta el día, se usa la anterior más cercana (o la siguiente) y se marca como estimada.
- **Cuotas:**
  - `cuota = total / cuotas`.
  - `cuotasPagadas = pagado / cuota`.
  - Próximo vencimiento = `primerVence` + cuotasPagadas × frecuencia. Si la frecuencia es mensual, se suman meses.

## 7. Formato de los CSV

Todos usan separador `;`, BOM UTF-8, fin de línea `\r\n`, coma decimal y comillas cuando hace falta. Los nombres de columna no distinguen mayúsculas ni acentos.

| Archivo | Columnas |
|---|---|
| `carteras.csv` | `libro;nombre;moneda;forma;orden;archivada` |
| `tasas.csv` | `fecha;fuente;origen;valor` |
| `<Libro>/<aaaa-mm>.csv` | `id;fecha;tipo;cartera;monto;destino;monto_destino;tasa;categoria;descripcion;deuda` |
| `deudas.csv` | `id;libro;sentido;nombre;moneda;total;fecha;cuotas;frecuencia_dias;primer_vence;es_gasto;categoria;notas;cerrada` |
| `recurrentes.csv` | `id;libro;tipo;cartera;monto;categoria;descripcion;frecuencia;proxima;automatico;activa` |
| `presupuestos.csv` | `libro;categoria;monto;moneda` |
| `faltantes.csv` | `id;libro;texto;comprado` |

**Valores válidos:**
- `moneda`: `BS`, `USD` o `USDT`.
- `forma`: `EFECTIVO` o `DIGITAL`.
- `fuente`: `BCV`, `BINANCE` o `BINANCE_V`.
- `origen`: `auto`, `manual` o `paralelo`.
- `frecuencia`: `SEMANAL`, `QUINCENAL` o `MENSUAL`.
- Campos sí/no: `si` o vacío.

**En los movimientos:**
- `tipo` admite *Entrada*, *Gasto*, *Transferencia*, *Ajuste* o *Apertura*.
- `cartera` es el nombre dentro del libro. `destino` puede ser `OtroLibro/Nombre`.
- `deuda` es el `id` de la deuda.
- Una cartera desconocida se crea sola, adivinando moneda y forma por el nombre.

**Cómo se detecta un cambio externo:**
- La app guarda el SHA-1 de cada archivo que escribe.
- Si al abrir el contenido difiere, el archivo se importa: el de un mes reemplaza ese libro y mes; los globales se fusionan por `id`.

## 8. Fuentes de tasas

| Uso | Endpoint |
|---|---|
| BCV hoy | `GET https://ve.dolarapi.com/v1/dolares/oficial` → `promedio` |
| BCV respaldo | `https://www.bcv.org.ve/`, bloque `id="dolar"` |
| BCV histórico | `GET https://ve.dolarapi.com/v1/historicos/dolares/oficial` |
| Paralelo histórico | `GET https://ve.dolarapi.com/v1/historicos/dolares/paralelo` |
| Binance P2P | `POST https://p2p.binance.com/bapi/c2c/v2/friendly/c2c/adv/search` con `{"asset":"USDT","fiat":"VES","tradeType":"BUY"\|"SELL","rows":5,...}` |

Cada fuente falla por separado y el error nunca se propaga a la interfaz: solo cambia el texto de estado.

## 9. Cómo extender la app

- **Nueva categoría:** añádela a `Categorias.GASTO` o `Categorias.ENTRADA` y, si quieres, sus palabras clave en `claves`.
- **Nueva fuente de tasa:** crea una constante en `Fuente`, consúltala en `ConsultaTasas.actualizar` y decide su uso en `TablaTasas.fuenteDe`.
- **Nuevo campo en una entidad:** dale un valor por defecto, sube la versión de la base de datos con `AutoMigration`, y añade la columna al export e import de `CarpetaSync`, conservando la compatibilidad con los CSV antiguos.
- **Publicar una versión:**
  1. Sube `versionCode` y `versionName` en `app/build.gradle`.
  2. Ejecuta `./gradlew testReleaseUnitTest assembleRelease`.
  3. Haz commit y etiqueta la versión.
  4. **Antes de un push público**, revisa que no se cuelen datos personales (CSV reales, rutas, nombres) ni la clave.

**Ideas para la fase 3:** widget de saldo y tasas, PIN o huella al abrir, gráficos de evolución, alerta de brecha Binance/BCV, y lectura local de las notificaciones del banco para proponer movimientos.

## 10. Consumo de recursos

Medido en un Poco X3 NFC (Android 15) con la app en segundo plano:

| Recurso | Consumo |
|---|---|
| RAM (PSS) | ~31 MB (comparable a KeePassDX, menos que el launcher) |
| CPU en reposo | 0 % |
| APK | ~1,5 MB |
| Datos en la carpeta | ~120 KB con un mes de datos, un PDF y un ZIP |
| Segundo plano | 1 tarea cada ~6 h de pocos segundos, respetando el ahorro de batería |
| Red | 3 consultas pequeñas como máximo cada 3 h |

---

*ElDuro es software libre bajo licencia GPL-3.0.*
