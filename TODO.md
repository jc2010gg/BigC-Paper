# TODO — pruebas de Don Pollos con clientes reales

Todo lo siguiente queda **pendiente de prueba manual**. Los tests automatizados no
validan sensaciones de combate, física, renderizado ni guardado/login de Paper.
Marcar cada casilla solo después de comprobarla en el juego.

## Preparación y registro

- [ ] Usar una copia del mundo y una configuración de prueba; probar como jugador
  sin OP. Usar permisos de administrador solo para preparar NPC y encuentros.
- [ ] Anotar commit/JAR, versión de Paper, BetterModel y configuración efectiva de
  `plugins/BigCasares/don-pollos.yml`. Los defaults nuevos no reemplazan un YAML existente.
- [ ] Preparar dos jugadores para cooperación y desconexiones; probar Java y, si se
  habilita Bedrock, Geyser/Floodgate con sus packs aceptados.
- [ ] Repetir las pruebas importantes en Normal y Hard; anotar equipo, encantamientos,
  comida, efectos, ping y TPS. Comparar siempre con el mismo equipo.

Registro de cada fallo: escenario, jugador/plataforma, victorias previas, equipo,
resultado esperado/obtenido, hora, video y fragmento del log. Registrar también
tiempo de combate, muertes, consumibles gastados y premio recibido.

## Boss: dificultad y fases

- [ ] Primera victoria: terminar las cuatro fases en supervivencia. Los umbrales son
  **75 %, 50 % y 25 %** de vida; comprobar títulos, ataques y Batallas del Cubo.
- [ ] Repetir con victorias previas: el daño/frecuencia y armadura de minions deben
  subir. La vida base sigue siendo **600 puntos**, salvo configuración distinta.
  Evaluar si el encuentro sigue siendo ganable y divertido con el equipo elegido.
- [ ] Golpear con maza, críticos, proyectiles y explosiones; probar varios jugadores
  golpeando juntos. El daño alto no debe saltarse las transiciones ni el cubo.
- [ ] Probar escudo, Fuerza, Resistencia, armadura encantada, tótem y balde KFC;
  comprobar que ninguna combinación trivialice el combate o cause daño inexplicable.
- [ ] Invocar, desconectarse durante el ritual y volver. La dificultad debe conservar
  las victorias del invocador, aunque no esté conectado al terminar.
- [ ] Cuenta nueva invoca junto a veterano; luego repetir con veterano que llega y
  golpea después. El nivel debe tener en cuenta al veterano y nunca disminuir.
- [ ] Comprobar los topes con encuentros de nivel alto y dos bosses simultáneos.
  Defaults: **24 gallinas**, **12 Sanders**, **40 Labubus** por encuentro, y **80
  minions del boss por mundo**. El límite de Labubus no incluye al Coronel.
  Registrar TPS/MSPT y comprobar que las rondas siguen pudiendo terminar.
- [ ] Probar láser con otro jugador delante y una pared detrás; debe dañar al
  interceptor alcanzado y respetar la pared. Verificar mensajes de muerte de láser,
  meteorito, rayo, onda y embestida.
- [ ] Probar megasalto en ladera, bajo techo y cerca de un Nexus. No debe encajar la
  hitbox ni extraer/colocar bloques en zonas protegidas o cubos de otro encuentro.

## Cubo: progreso, salidas y terreno

- [ ] Completar las tres rondas; comprobar cantidades, Coroneles, premio visual y
  regreso seguro a la superficie.
- [ ] Forzar reubicación por zona protegida hacia una meseta/chunk vecino: el jugador
  debe recibir enemigos y poder completar las rondas aunque esté lejos de la barra del boss.
- [ ] Dos jugadores en cubos vecinos y dos bosses cercanos: sin terreno superpuesto,
  bloqueos cruzados ni pérdida de bloques al restaurar.
- [ ] Un jugador sale del servidor dentro del pozo y otro termina la pelea; al
  reconectar debe aparecer en una salida segura. Repetir después de reiniciar Paper.
- [ ] Todos salen o mueren: comprobar limpieza tras el abandono y que quien vuelve
  no aparezca dentro de terreno rellenado. La política actual caduca las marcas de
  salida a los siete días: decidir si ese plazo sirve para el servidor.
- [ ] Probar perlas, chorus, portales, elytra, Corriente, viento, agua y monturas;
  comprobar que no permiten dejar la pelea bloqueada ni impiden una recuperación legítima.
- [ ] Restaurar sobre cofres simples/dobles con objetos, hornos y bloques con datos.
  Repetir con fluidos, pistones y explosiones cercanas: sin duplicación ni pérdida
  de contenido, objetos del mundo o construcciones originales.

## Premio final del boss

Premio actual: **un Balde de KFC dado vuelta + cuatro objetos a elección**, una
unidad por selección. El premio pertenece al ganador registrado, no a toda la party.

- [ ] Verificar cantidades exactas: un balde y cuatro unidades, sin stacks extra.
  Si hay poco espacio, conservar el premio dejando los sobrantes en el suelo.
- [ ] Revisar el catálogo: sin objetos del End, diamante, netherita o escombros
  ancestrales. Las plantillas de herrería son una excepción; como máximo una
  plantilla y un disco entre las cuatro selecciones.
- [ ] Cerrar/reabrir, cambiar páginas y categorías, hacer doble click, shift-click,
  teclas numéricas, intercambio con offhand y arrastrar. Sin sacar iconos del menú
  ni cobrar dos veces. Probar también con inventario y cursor llenos.
- [ ] Ganador desconectado: otro jugador no puede abrir ni reclamar su premio.
  Volver con el ganador y comprobar que todavía puede cobrarlo una sola vez.
- [ ] Cobrar justo antes de un reinicio limpio y volver: sin segundo cobro y sin
  pérdida de lo ya entregado. Repetir guardado/descarga del chunk después del cobro.
- [ ] Equipar el balde: Velocidad II mientras está puesto y mejora acuática;
  quitarlo y esperar a que se agote el efecto. Comparar con botas de Agilidad
  acuática y otras fuentes de velocidad. No debe quitar un casco con Ligamiento.
- [ ] Comparar esfuerzo/costo con el premio. Comprar toda la ofrenda cuesta por
  defecto **85 panes + 5 diamantes**. Evaluar acceso temprano a objetos raros aún
  permitidos (p. ej., maza, núcleo pesado, estrella del Nether o tótems).
- [ ] Decidir si el premio al rematador sirve para grupos: probar distinto invocador,
  principal atacante y último golpe; considerar reparto para evitar robar victorias.

## Salsero: dificultad y pago

- [ ] Completar varias victorias: primeras dos pagan **2 diamantes**, siguientes **1**.
  Con defaults, el cooldown de pago es **30 minutos**; ganar dentro del cooldown
  puede contar como victoria pero no debe dar otro premio.
- [ ] Verificar el cooldown antes/después de reconectar y reiniciar, y que el premio
  pendiente de una victoria ya ganada no se pierda por el cooldown.
- [ ] Probar Java y joystick Bedrock con ping real: diagonales, giro de cámara,
  teclas mantenidas, varias teclas juntas, golpes/empujes, agua e inercia.
- [ ] En niveles altos, comprobar que la ventana no baje del piso configurable
  (**6 ticks = 0,3 s** por defecto). Evaluar si resulta jugable para humanos.
- [ ] Desconectarse mientras el Salsero se acerca o entrega diamantes; volver y
  recibir exactamente lo pendiente. Un segundo jugador debe poder iniciar sin
  interferencia de tareas de la sesión anterior. Repetir desactivando/recargando el módulo.

## Ruleta, comercio y recompensas combinadas

- [ ] Verificar pagos: rojo/negro **×2**, verde **×20**; perder descuenta la apuesta.
  Desconectarse en giros ganadores/perdedores no debe devolver selectivamente pérdidas.
- [ ] Ganar con una manzana dorada encantada al verde: con el límite default de **2**,
  deben quedar como máximo dos en inventario y el excedente caer como objetos;
  comprobar cantidad total y metadatos. Repetir al desconectarse y con inventario lleno.
- [ ] Intentar apostar shulkers/bundles/contenedores, objetos custom y encantados;
  comprobar qué apuestas se permiten y que no se copien contenidos restringidos.
- [ ] Abrir/cerrar menús de ofrenda, comercio y ruleta durante logout y recarga:
  conservar exactamente ingredientes/apuestas/premios, sin extraer decoraciones.
- [ ] Comprar salsa/picante y usarlos con KFC y equipo de PvP. Evaluar Fuerza I y
  Velocidad III durante 10 s frente a sus precios de dos/tres panes.
- [ ] Registrar rendimiento económico por hora de juego normal y de repetición:
  panes, diamantes, consumibles y objetos raros obtenidos. Ajustar configuración
  antes de habilitar la mecánica en la economía principal.

# TODO — pantallas de Tideframe (misiones y tienda) con clientes reales

Pendiente de prueba manual: los tests cubren reglas, contratos de pantalla y que BigCasares cargue sin
Tideframe, no el dibujo ni los clics en un cliente. Ver [docs/tideframe.md](docs/tideframe.md).

- [ ] Servidor de prueba con BigCasares, BetterModel y Vault con una economía; SIN Tideframe.jar externo.
  Confirmar el modulo integrado y las pantallas en `plugins/BigCasares/tideframe/engine/screens`.
- [ ] Java con los dos packs aceptados: `/misiones` y un mercader (NPC; `/shop` está desactivado) abren las
  pantallas de Tideframe, se leen enteras
  a la escala de GUI habitual y la X cierra. Sin el pack de Tideframe se abren los menús de inventario.
- [ ] Tienda: un clic izquierdo compra una vez y uno derecho vende una vez (saldo e inventario antes y
  después), con inventario lleno, saldo insuficiente, objetos custom (PDC, tier, modelo, stack) y páginas.
- [ ] Doble clic sobre una categoría y clic apenas cambia la página: no se compra nada; medio segundo después
  sí. Anotar si la espera se nota con el ping real.
- [ ] Recargar la tienda o BigCasares con la pantalla abierta: no se compra nada desde una vista vieja.
- [ ] Misiones: progreso, "¡Lista!" y "Cobrada" coinciden con el menú de inventario; "Reclamar" (clic
  izquierdo) cobra una sola vez sin cerrar la pantalla, la fila pasa a "Cobrada" y el botón desaparece; un doble
  clic no paga dos veces; el clic derecho no hace nada.
- [ ] Tienda: "Cant." pasa por x1, x4 y x16; las filas muestran nombre y precio de esa cantidad; un clic
  compra o vende todos los lotes con un solo movimiento de saldo, o ninguno si no alcanza el saldo o los ítems.
  Probar con inventario casi lleno (lo que no entra cae al piso) y con objetos custom.
- [ ] Tienda con poco saldo: la fila dice "falta $X" (Java) o va en rojo con "(faltan $X)" (Bedrock), y tocarla
  no cobra nada. En Bedrock, "Cantidad" alterna x1, x4 y x16 y una fila compra esa cantidad; "Modo: vender"
  hace que tocar una fila venda esa cantidad (un solo pago) y muestra cuántos llevás si no alcanzan.
- [ ] Completar una misión jugando: llega un solo aviso "¡Misión lista!" en el chat y, al abrir /misiones,
  esa misión está primera.
- [ ] `/bigcasares tideframe` como operador: los números coinciden con los jugadores conectados y el pack;
  abrir la tienda saltando suma una caída "en el aire".
- [ ] Shift + clic derecho sobre un producto (Java): vende todos los lotes enteros de una vez, un solo pago.
- [ ] Sonidos: compra o venta correcta, rechazo y cobro de misiones se oyen solo para quien los hizo (Java y Bedrock,
  también en el menú de inventario clásico), una sola vez por operación.
- [ ] `/bigcasares historial`: muestra tus últimas compras y ventas de cualquier menú, la más nueva primero, también después de reiniciar el servidor.
- [ ] Favoritos: Shift + clic izquierdo (Java) y modo favoritos (Bedrock) marcan y desmarcan; "★ Favoritos" aparece
  primera, compra el producto real y sobrevive a salir y reiniciar el servidor; vacía, desaparece.
- [ ] Misiones: cobrar suma a "esta semana $X" (pestaña Semanales) y se reinicia con la semana; cobrar todas las
  diarias suma un día a la "racha" (Diarias), que se corta si pasa un día sin hacerlo. Ambos sobreviven a reiniciar.
- [ ] Entrar con misiones listas sin cobrar: a los 3 segundos llega "Tenés N misiones para cobrar"; sin nada, no llega.
- [ ] "Cambiar" en Diarias (Java) y "Cambiar las diarias" (Bedrock): el primer toque pregunta, el segundo cambia;
  no aparece con una diaria lista sin cobrar ni dos veces el mismo día; `/misiones reroll` también se niega.
- [ ] Con PlaceholderAPI en un scoreboard: `%bigc_misiones_listas%`, `%bigc_misiones_premio%`, `%bigc_tienda_ultima%`.
- [ ] Un producto o misión con nombre largo: se achica o termina en "…" sin pisar la columna de al lado.
- [ ] Tocar el mercader o pedir `/misiones` dos veces seguidas con la pantalla abierta: los botones siguen funcionando.
- [ ] `/tideframe reload` con una pantalla `bigc_*` abierta: no vuelve sola, el jugador ve el aviso y el
  comando la abre de nuevo. Lo mismo con `/tideframe open bigc_tienda`.
- [ ] Tideframe con `pack.send: false`: el log lo avisa y se abren los menús de siempre.
- [ ] Cambiar `tideframe/engine/config.yml` y recargar el motor: el estado real de `pack.send` y el UUID del pack
  deben decidir la elegibilidad; nunca aceptar solamente el pack de BigCasares como prueba del pack de UI.
- [ ] Tocar un mercader justo mientras se cierra otra pantalla de Tideframe: la tienda conserva sus filas y clics.
- [ ] Planeando, con el gancho, en vehículo o en el aire: se abre el menú de inventario, no Tideframe.
- [ ] Bedrock (Geyser/Floodgate): el mercader y `/misiones` abren formularios, nunca la pantalla Java de Tideframe.
  Después de comprar o vender, el formulario de la categoría vuelve a abrirse con el saldo nuevo.
- [ ] Bedrock con el pack de BigCasares (versión 2.1.0) aceptado, en PC y en un teléfono: tienda y misiones se ven
  con el panel oscuro de borde dorado, título dorado, filas con íconos y la X; se leen enteras y la lista se
  desplaza. Comparar con capturas de las pantallas Java.
- [ ] Bedrock: tocar una fila compra una vez; las pestañas Diarias/Semanales cambian la lista; "Reclamar" cobra
  una sola vez y vuelve a mostrar la lista. Probar también con mando (el foco no debe quedar en botones ocultos).
- [ ] Bedrock: un formulario de otro plugin o de Don Pollos conserva el aspecto de Bedrock.
- [ ] Bedrock sin el pack (`geyser-integration.bedrock-resource-pack: false`): los formularios funcionan con el
  aspecto de Bedrock, sin caracteres raros en el título y con barras de texto en las misiones.
- [ ] Bedrock con otro pack que también cambie `server_form.json`: anotar si se pisan.
- [ ] Editar una pantalla en el editor de Tideframe y reiniciar: la edición se conserva.
