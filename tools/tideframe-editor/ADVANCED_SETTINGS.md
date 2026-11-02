# Ajustes avanzados del editor — 22/10/2026

Este cambio afecta únicamente al editor de creación. Parte del código actual de Claude (0.22 y cambios de 0.23 en curso), incluidos sus cambios sin commit. No cambia el plugin, sus demos, assets, archivos de pantallas ni servidores.

## Usarlo

Abrir el editor y activar **Advanced mode: ON**, en la columna izquierda. Los ajustes de página aparecen allí; los del elemento seleccionado aparecen en el inspector derecho. Cada grupo se puede plegar. Apariencia, texto y área de clic también tienen su propio grupo plegable. En escritorio los paneles laterales desplazan su contenido sin perder el lienzo.

**Blur, glass and shadows** aparece como sección propia, abierta al activar el modo avanzado. Permite regular los tres difuminados (0–16 píxeles lógicos) y aplicar **Soft blur**, **Frosted glass**, **Soft shadow** o **Clear blurs** a la selección desbloqueada. El vidrio activa fondo y opacidad 0.82; la sombra suave activa la sombra. Los presets reemplazan los valores de difuminado anteriores; Clear blurs conserva colores y transparencia. Deshacer recupera el aspecto previo. Brillo, opacidad y los demás estilos siguen en Appearance.

- Los campos simples se guardan al editarlos. **Default** o vacío conserva la opción heredada; no agrega opciones al documento solo por mostrarlas.
- Variables, funciones, teclas, pasos de animación y eventos usan borradores con **Apply** / **Revert draft**. **Example → draft** prepara un ejemplo y no reemplaza el documento hasta aplicarlo.
- En un evento, **Add an action** permite elegir entre 24 tipos, editar sus campos y añadirlo al borrador. **Apply changes** guarda la lista. Las ramas de condiciones, parámetros compuestos y listas de acciones anidadas se editan como JSON; no se presenta como un editor de nodos.
- Deshacer/Rehacer, autosave, exportar e importar siguen usando el historial existente. Una entrada rechazada conserva el último documento válido. Mientras haya borradores JSON sin aplicar, un aviso muestra cuántos quedan; se impide cambiar de selección/página, reemplazar lógica, renombrar o exportar hasta aplicar o revertir. Cerrar o recargar el navegador también pide conservar esos cambios. Los campos JSON completos de pantalla y elemento tienen Apply/Revert explícitos: salir del campo no los guarda.
- La selección bloqueada desactiva los controles del elemento. Las acciones de interacción se habilitan para botones; etiquetas y decoraciones conservan sus ajustes visuales y de visibilidad.

## Cobertura de las funciones existentes

| Función del plugin | Control en el editor |
| --- | --- |
| Comando, permiso, abrir al entrar | Page settings → Access |
| Cerrar con Shift, daño, F, Shift+F; volver a la pantalla anterior | Closing rules |
| Entrada y salida: fade / slide / none, duración, curva, lado y distancia | Page transitions |
| Compatibilidad de cierre invertido (fundido) | Legacy reverse close, con advertencia de que reemplaza el efecto de cierre |
| Variables locales/globales y funciones con parámetros | Variables and functions, con ejemplos |
| Teclas y acciones al abrir/cerrar | Keyboard and lifecycle |
| Oculto, condición de visibilidad, arrastre de dos clics, tooltip | Visibility and interaction |
| Clic izquierdo/derecho, entrar/salir del cursor, recibir un arrastre | Event actions |
| Comandos del jugador/consola, chat, actionbar, sonido, teleport, demora, ítems, abrir/volver/cerrar, visibilidad, variables, condiciones, permisos, funciones, input, offset, tint, animation, scroll | Constructor de 24 tipos de acciones en cada evento (los alias se conservan en JSON) |
| Keyframes: disparador, curva y pasos de posición, escala, opacidad y rotación | Keyframe animation |
| Carpeta PNG / GIF, intervalo opcional y bucle | PNG / GIF animation |
| Fuente de lista, filas, espaciado, filtro y selección | Data list; las listas nuevas empiezan con una fila y se valida que la última permanezca dentro del lienzo |
| Difuminado de elemento/fondo/sombra, brillo, opacidad, gradientes y texto | Appearance, text and hit area (controles previos conservados) |
| Imagen normal y de hover | Load image / Load hover image (controles previos conservados) |
| Cursor por página y modo menú/HUD | Game presentation (controles previos conservados) |

No hay controles nuevos para API Java, integraciones o configuración global del servidor: no son propiedades de una pantalla. `keep-open` y `close-on-death` se conservan si ya estaban importados, pero no se ofrecen como funciones distintas: el runtime los admite por compatibilidad y morir siempre cierra.

## Límites reales

- La vista del editor no ejecuta acciones, condiciones, permisos, listas ni diálogos del juego. Configurarlos y exportarlos no demuestra su ejecución en Minecraft.
- Slide de página y offset en vivo son horizontales. Los movimientos verticales de elementos se pueden prehornear con keyframes.
- Un elemento no puede usar frames y keyframes a la vez; una lista tampoco puede usar frames/GIF ni keyframes. **Remove …** permite quitar una de esas funciones sin borrar las demás.
- `frames.folder` referencia `plugins/BigCasares/tideframe/engine/images/`, no sube archivos. Para `images/fire.gif`, escribir `fire`. Intervalo vacío conserva el ritmo automático; no escribe 50 ms por accidente. **Load image** sigue importando una imagen estática.
- El difuminado de fondo usa el arte horneado, no el mundo del juego ni otras capas dinámicas. Los cuadros y capas adicionales aumentan el pack.
- El servidor vuelve a validar el JSON, las referencias y los recursos. Los ejemplos son puntos de partida: ajustar nombres, permisos, destinos y recursos al servidor propio. Las acciones de consola dependen de la lista permitida del operador.

## Verificación reproducible

Desde la raíz del repositorio:

```powershell
node --check tools/tideframe-editor/app.mjs
node --check tools/tideframe-editor/advanced-settings.mjs
node --check tools/tideframe-editor/advanced-ui.mjs
node --test tools/tideframe-editor/*.test.mjs
node tools/tideframe-editor/serve.mjs
```

Las pruebas nuevas cubren preservación de lógica anidada, exportación/importación, claves camelCase, límites, acciones, conflicto de animaciones, eliminación de funciones y la lista cerrada de módulos HTTP. La comprobación manual de navegador incluye constructor de acciones, exportación real, lista, ocultar, GIF automático, bloqueo, tipo de elemento y Deshacer/Rehacer.

Evidencia local de esta tarea: `work/editor-advanced-20261022/` en el workspace padre del repositorio. Allí están el respaldo previo del editor, los logs, siete JSON aceptados por el `ScreenCodec` Java actual (uno exportado desde el navegador), el comprobador Java y los hashes de los archivos protegidos fuera del editor. Esa aceptación comprueba el formato, no una sesión de juego. No se atribuye una auditoría independiente a Claude.

La revisión integral posterior y sus 101 exportaciones están documentadas en [REVIEW_COMPLETE_20261022.md](REVIEW_COMPLETE_20261022.md).
