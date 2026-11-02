# Tideframe screen editor - schema 4 a 9

Editor local original, sin bibliotecas web, servicios externos ni assets comerciales. Desde el 28/10/2026 toda la interfaz está en castellano (`i18n.mjs` traduce al mostrarlos los mensajes de los módulos, que siguen en inglés para sus tests). El codigo y la fuente original pertenecen a la licencia MIT del repositorio; las fotos que cargue el operador conservan sus propios derechos.

## Iniciar y publicar

Desde la raiz del repositorio, con Node.js24 o compatible:

```sh
node tools/tideframe-editor/serve.mjs --server RUTA_DEL_SERVIDOR
node --test tools/tideframe-editor/*.test.mjs
```

Abrir http://127.0.0.1:4176. El servidor escucha exclusivamente en loopback y sirve una lista cerrada de archivos. `--server` selecciona `RUTA_DEL_SERVIDOR/plugins/BigCasares/tideframe/engine/screens`. Aparece **Publicar en el servidor**: escribe la pantalla validada en esa carpeta, y el módulo la recarga solo (auto-reload) y pide a los jugadores aceptar el pack nuevo. También lee, solo para mirar, los GIF y PNG de `plugins/BigCasares/tideframe/engine/images` que usa el modo **Probar**. Sin carpeta seleccionada, **Exportar pantalla JSON** descarga `<screen-id>.json` para guardarlo en `plugins/BigCasares/tideframe/engine/screens/`.

Se conserva la selección directa con `--screens RUTA_DE_PANTALLAS` o `TIDEFRAME_SCREENS`. La prioridad es `--screens`, después `--server`, después la variable de entorno. Sin ninguna de estas opciones, publicar y el puente al juego quedan apagados.

Los menús no se editan dentro del juego desde la 0.23 (`editing.in-game: false`): `/tideframe edit` está bloqueado. Se abren con `/tideframe ID` o con su comando propio.

Con una carpeta seleccionada, después de **Publicar** el editor espera a que el módulo cargue ese archivo (lo reconoce por su fecha) y dice si quedó activo, con sus avisos, o por qué falló la recarga. **Abrir en el juego** le pide al módulo que abra la versión publicada de la página para el único jugador conectado; si hay varios, pregunta el nombre y lo recuerda. Los dos usan `plugins/BigCasares/tideframe/engine/editor-status.json` y `editor-open.json`, y necesitan el módulo Tideframe de BigCasares habilitado.

**Íconos de ítems** (campo "Ícono de ítem del juego", esquema 9): el juego dibuja la textura del ítem que ya tiene; el pack no copia nada. Para la vista previa el editor lee esa textura de tu propio Minecraft 26.2 (`%APPDATA%/.minecraft/versions/26.2/26.2.jar`, u otro con `TIDEFRAME_CLIENT_JAR`); sin él, el ícono se ve como un recuadro con su nombre y en el juego sale igual. Solo entran en la parte de abajo del menú: el borde de abajo del ícono en y 64 o más abajo, de 144 (el editor marca la franja prohibida). El tamaño sale de la caja: 16, 32, 48 o 64 píxeles. El juego no puede atenuar, girar, difuminar ni sombrear la textura: un elemento con ícono necesita opacidad 1 (0 lo oculta junto con el ícono), sin giro, difuminado, sombra ni cambio de brillo, y hover "ninguno" o "brillo". Los bloques se ven como una cara plana, los ítems que el juego tiñe (cuero, pociones, hojas) se ven sin ese color, y los animados muestran su primer cuadro.

**Bordes fijos (9-slice)** en la sección de imagen: con "Bordes fijos" mayor que 0 la imagen llena la caja, las esquinas no se deforman y los lados y el centro se estiran. "Píxeles de la imagen por píxel del menú" en 2 o más da bordes más finos con el mismo PNG más grande.

**Navegar con el teclado** (Avanzado → Teclas): W A S D llevan al botón más cercano en esa dirección y Espacio hace clic; las teclas asignadas a mano siguen mandando. Probar lo simula.

**Probar** usa el menú en el navegador: hover, clics, páginas con sus transiciones, visibilidad, variables, condiciones, funciones, teclas, listas, fotogramas clave, desplazamiento, color y GIF o PNG del servidor. Lo que solo pasa en el juego (comandos, mensajes, sonidos, ítems, teletransporte, diálogos, datos de otros plugins) se anota en "En el juego haría…". Los permisos se eligen con una casilla. La vista sigue siendo aproximada: alineación, tamaño real, cámara y cursor del juego se comprueban en Minecraft.

## Calidad seleccionable

| Selector | Raster |
|---|---|
| Baja - Minijuegos | 512 x 288 |
| Media - 1080p | 1920 x 1080 |
| Alta - 1440p | 2560 x 1440 |
| Personalizada | 256 x 144 a 3840 x 2160 |

Personalizada exige16:9 exacto y ancho multiplo de32. Baja evita frames de animacion de entrada y necesita menos tiles; ninguna calidad garantiza descarga instantanea. Estas dimensiones pertenecen al raster del pack y no equivalen a pixeles fisicos de pantalla. Las pantallas nuevas usan Baja. JSONv1-v3 conserva sus valores al leer, pero al publicar desde el editor se migra a v4 personalizada1024 x576. Los originales no se sobrescriben. Si un archivo antiguo contiene botones superpuestos, la publicacion lo rechaza sin eliminar acciones.

## Seleccion, copiar, pegar y resize

- Shift + click en lista o preview alterna seleccion multiple. Arrastrar un miembro ya seleccionado conserva el grupo. El inspector edita solamente el elemento activo.
- Caps Lock OFF: arrastrar mueve el grupo; Caps Lock ON: cambia dimensiones con centros fijos. Derecha/abajo agranda. Apagar Caps Lock termina el resize y exige un nuevo gesto para retomarlo. Ctrl no activa resize. Si el navegador no informa Caps Lock, usar los campos Width/Height.
- Flechas mueven0.02; Shift + flecha mueve0.1. Delete elimina el grupo. Ctrl+C/Ctrl+V y los botones Copy/Paste usan JSON estricto y dejan los atajos nativos dentro de campos de texto.
- Si falla el permiso de clipboard, se informa y se usa la copia interna. Nunca se envian contenidos online. El ID del sobre es `tideframe_clipboard`, con titulo `TIDEFRAME CLIPBOARD`.
- Pegar crea IDs `_copyN` y traslada el grupo conservando separaciones y areas de accion. Busca posicion libre, con limite50000 candidatos. Si no encuentra, rechaza toda la operacion sin cambiar el documento ni descartar acciones. Mover elementos existentes y reintentar.
- Los IDs temporales vacios o repetidos desactivan exportar, sin fusionar selecciones de filas. El grupo conserva su identidad al corregir el ID.

## Modo avanzado y acciones

Advanced mode muestra opacidad0..1, fill/border/text color, drop shadow, layer entero-128..128 y area de accion independiente. Su orden de dibujo es estable por layer; empates conservan el orden del documento. El preview reutiliza nodos de fotos y valida el presupuesto sin volver a serializar todas sus cadenas Base64 durante cada movimiento.

Los rectangulos discontinuos muestran las areas de accion. Por defecto usan bounds visuales; el checkbox permite un centro y dimensiones independientes, absolutos y normalizados. Mover o pegar traslada tanto el elemento como su area. Resize visual conserva el area independiente. Todos los interiores de botones deben estar separados; tocar bordes esta permitido. Cualquier solapamiento desactiva exportar con un mensaje explicito. Opacidad0 o pixels transparentes no desactivan acciones.

Label y Button parten transparentes y con borde visible. Background controla el relleno por separado. Shape admite rectangle, rounded y ellipse. Add decoration parte sin texto, fondo, borde ni accion; permite fotos y estilo, es editable aqui y no recibe hover/click en Minecraft. Las areas de juego siempre son rectangulares, incluso para elipses e imagenes transparentes.

Acciones permitidas: vacia, `close`, `open:screen_id`, `message:literal` y `conditional-message` en botones. No existen comandos, scripts, HTML, expresiones ni evaluacion de placeholders. Conditional message requiere permiso minusculo como `tideframe.vip`, y mensajes ASCII literales para true/false; el plugin comprueba el permiso al activar el boton. Los destinos de `open:id` deben estar instalados por el operador.

## Novedades 0.23: ideas muy buenas (21 a 45)

- **Capas:** la lista de elementos va de la capa de arriba a la de abajo.
  - Arrastrar una fila cambia su capa; si entra un número entre sus vecinos, cambia solo ese elemento.
  - Doble clic (o F2) renombra el elemento, y las acciones, tooltips y listas que lo nombran se actualizan.
  - El ojo lo oculta solo en el editor y el candado lo bloquea.
- **Copiar y pegar estilo:** botones al lado de Duplicar, o Ctrl+Alt+C y Ctrl+Alt+V. Copia colores, borde, forma, sombra, estilo del texto, efectos y difuminados del elemento activo y los pega en toda la selección. Nunca copia el texto, la posición, el tamaño, la imagen ni la lógica.
- **Mapa del proyecto:** botón en Páginas. Muestra cada página con flechas de qué botón lleva a cuál.
  - Las flechas van por los pasillos entre cajas, sin cruzarlas.
  - Marca las páginas a las que no se llega y los enlaces a páginas que no existen.
  - Tocar una caja lleva a esa página.
- **Íconos originales:** 29 íconos de pixel art dibujados por el editor mismo, sin licencias de terceros (`tools/tideframe-editor/icon-library.mjs`). Entran como imagen de 32 píxeles donde no haya otra imagen.
- **Componentes nuevos:**
  - "Confirmación con el estilo del menú": un botón muestra la pregunta, y Sí y No la ocultan.
  - "Pestañas con paneles": cada pestaña muestra su panel y una barra se desliza bajo la elegida.
- **Plantillas de HUD** en "Página desde plantilla": vida y hambre, coordenadas y mundo, dinero, y objetivo con progreso. Las barras de variables (`{bar:progreso/10}`) ahora también se ven como barra en el editor.
- **Probar:**
  - Panel "Variables de la prueba": cambiar variables (corre "Cuando cambia una variable"), los valores del jugador simulado (vida, saldo, mundo…) y placeholders simulados (`%clave% = valor`).
  - Botones "Simular daño" y "Cambiar de mundo".
  - Los temporizadores corren de verdad.
  - Pausa y salto de cuadro en GIF y PNG, con el mismo muestreo del plugin.
- **Textos calculados** (`{monedas * 2}`) en la vista previa y en Probar, con los mismos decimales que el plugin.
- **Casos compartidos con el plugin:** `tools/tideframe-editor/cross.test.mjs` corre en Probar los mismos casos que `CrossProbarTest.java` en el plugin. La copia del editor está en `tools/tideframe-editor/fixtures/cross`; los casos de ajuste de texto están en `tools/tideframe-editor/fixtures/textfit.json`.
- **HUD sumados y avisos:**
  - Casilla "Se suma a los HUD que ya se ven" en la presentación de una página HUD.
  - Acciones "Sumar una página HUD", "Quitar una página HUD" y "Mostrar un aviso (toast)". Probar las anota en el registro, con los segundos que usaría el plugin.
  - Plantillas "Aviso que entra y se va" y "Logro desbloqueado".
  - Revisar avisa si una de esas acciones apunta a un menú en vez de a una página HUD.
- **Colores por palabra:** `<gold>palabra</gold>` o `<#ffcc00>palabra</#ffcc00>` en el texto, con las reglas de `richtext.mjs` (las mismas de `RichText.java`).
  - La vista previa y Probar pintan cada palabra con su color.
  - Revisar mide el texto sin las etiquetas.
  - Escribir una etiqueta en una página vieja la pasa al esquema 9, y lo que ya decía `<gold>` como texto queda escapado (`\<gold>`).
- **Sonidos propios:** "Subir sonido .ogg" en el selector de sonidos, con el editor conectado al servidor.
- **Imagen recortada:** casilla de estilo para que la foto tome las esquinas redondeadas o la elipse del elemento.

## Fotos y seguridad de publicacion

Load image admite PNG, JPG/JPEG, WebP, AVIF, BMP y GIF cuando el navegador los decodifica. HEIC/TIFF depende del decoder disponible: guardar como JPG/PNG si falla. SVG/XML se rechaza. Input maximo64 MiB y40,000,000 pixels; este ultimo limite se aplica despues de decodificar, por lo que el decoder puede asignar memoria propia antes.

Al cargar una foto, el lado mayor se ajusta al ancho de calidad seleccionado, sin ampliarla, conservando proporcion y con maximo3840 por lado y8,294,400 pixels. Las fotos existentes no se reescalan al cambiar calidad ni recuperan detalle perdido: volver a cargar la original. Salida: PNG estatica maximo40 MiB. Animaciones pasan a un frame capturado; no se exporta timing ni se garantiza exactamente cual frame en la ruta Image.decode. El canvas utiliza resampling high y puede cambiar perfiles de color o RGB bajo pixels completamente transparentes.

El PNG se comprueba por firma, Base64 canonico, bounds, CRC, IHDR, paleta, tRNS, orden, ausencia de APNG y final IEND. Se valida el stream zlib completo, dimensiones exactas, bytes de scanlines y filtros, incluido Adam7, con DecompressionStream y tope derivado del IHDR. El navegador ademas debe decodificar y leer el PNG con canvas. Un archivo JSON importado no puede referir URLs, rutas o data URLs como imagen: solamente PNG Base64 canonico o null. El backend valida independientemente y elimina metadata.

Importar y pegar validan cada imagen distinta antes de publicar. El ultimo pedido asincrono vigente gana; cambiar seleccion, borrar o editar invalida resultados anteriores, incluidos errores y mensajes de estado. Un rechazo conserva el documento anterior. Remove image elimina solo la foto. Vaciar Text permite un boton con imagen solamente.

## Contrato JSON

Todos los campos mostrados son obligatorios y las claves desconocidas se rechazan:

```json
{
  "version": 4,
  "id": "menu",
  "title": "TIDEFRAME",
  "quality": { "preset": "low", "width": 512, "height": 288 },
  "elements": [{
    "id": "start", "text": "START", "x": 0, "y": 0,
    "width": 0.8, "height": 0.18, "kind": "button", "action": "message:Hello.",
    "shape": "rectangle", "border": true, "messageCondition": null,
    "background": false, "image": null,
    "style": { "opacity": 1, "fillColor": "#28384b", "borderColor": "#ffffff", "textColor": "#ffffff", "shadow": false },
    "layer": 0, "actionArea": null
  }]
}
```

IDs: `[a-z][a-z0-9_-]{0,31}`, unicos por elemento. Titulo1..40 y texto0..64 caracteres ASCII imprimibles. Centros x/y en[-1,1] con Y positivo arriba, dimensiones>0 y<=2. Un actionArea independiente tiene exactamente x,y,width,height con iguales limites. Decoration exige action vacia y messageCondition null. Colores#RRGGBB y shadow booleano. `messageCondition` tiene exactamente permission,ifTrue,ifFalse; permiso `[a-z0-9_.-]{1,128}`, branches ASCII hasta100. Otras acciones usan null.

V4 admite128 elementos, legacy24; maximo128 MiB UTF-8 por documento, nesting8. Los JSON con claves duplicadas, incluidas escapes equivalentes, se rechazan. Version y los enteros de quality/layer se comprueban desde tokens numericos originales para evitar redondear valores fraccionarios de alta precision. `4.0` y `4e0` equivalen a4; `4.00000000000000001` se rechaza. Preset custom no acepta un nombre legacy.

## Fuente original

La herramienta original de regeneración es `tools/make-web-font.py` del repositorio Tideframe de origen; no forma parte de esta copia del editor.

Regenerar la fuente requiere Pillow y fonttools4.60.1 como herramientas de build y nuestro `build/packs/tideframe.zip`; no son dependencias runtime. FONT_PROVENANCE.json registra hashes. TTF/WOFF contiene nuestros95 glifos ASCII; lowercase usa nuestros trazos uppercase, igual que el atlas original. Codigo y fuente conservan licencia MIT del repositorio.
