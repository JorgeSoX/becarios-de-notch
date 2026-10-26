# Becarios de Notch

Mod de **Fabric** para **Minecraft Java 26.3** que añade compañeros de trabajo: los **becarios**. Los contratas con esmeraldas y trabajan para ti: minan, talan, fabrican objetos, construyen casas y portales, te defienden y te siguen a donde vayas, incluso a través de portales. Todo se maneja con un panel y menús en pantalla; no hace falta escribir comandos.

- Cada jugador tiene **sus propios becarios** (hasta 5) y solo él puede darles órdenes.
- Los becarios **no se teletransportan nunca**: van andando, y si no encuentran camino te lo dicen.
- Funciona en **singleplayer y en servidores** (hay que instalarlo en los dos lados).

---

## Índice

1. [Instalación](#instalación)
2. [Primeros pasos](#primeros-pasos)
3. [Contratar becarios y precios](#contratar-becarios-y-precios)
4. [El panel (tecla B)](#el-panel-tecla-b)
5. [El menú de un becario](#el-menú-de-un-becario)
6. [Minar y talar](#minar-y-talar)
7. [Fabricar](#fabricar)
8. [Construir](#construir)
9. [Combate](#combate)
10. [Jefes y equipos](#jefes-y-equipos)
11. [Seguirte por portales](#seguirte-por-portales)
12. [Inventario, objetos y brújula](#inventario-objetos-y-brújula)
13. [Si muere, si se atasca](#si-muere-si-se-atasca)
14. [Comandos](#comandos)
15. [Preguntas frecuentes](#preguntas-frecuentes)
16. [Desarrollo y compilación](#desarrollo-y-compilación)
17. [Licencia](#licencia)

---

## Instalación

**Requisitos**

| Qué | Versión |
|---|---|
| Minecraft Java | 26.3 |
| Fabric Loader | 0.19.5 o superior |
| Fabric API | 0.163.0+26.3 |
| Java | 25 |

**Pasos**

1. Descarga `becarios-de-notch-<versión>.jar` (apartado *Releases* del repositorio, o compílalo tú: ver [Desarrollo](#desarrollo-y-compilación)).
2. Cópialo a la carpeta `mods` de tu instancia de Minecraft.
3. **Si juegas en un servidor**, ponlo también en la carpeta `mods` del servidor. Servidor y jugadores deben tener **la misma versión del jar**: el mod añade un bloque, una criatura y pantallas propias, y con versiones distintas no se podrá entrar.
4. Arranca. En el log del servidor debe aparecer `Becarios de Notch cargado.`

> Antes de añadir el mod a un mundo con partida seria, haz una copia de seguridad del mundo.

---

## Primeros pasos

1. Pulsa **B** para abrir el panel de becarios (la tecla se puede cambiar en *Opciones → Controles*).
2. Pulsa **Contratar**, elige un **nombre** y un **aspecto** (hay unas 30 skins).
3. Paga las esmeraldas (el primero cuesta 5; en creativo es gratis).
4. Haz **clic derecho sobre el becario** para abrir su menú y darle órdenes.

Alternativa a la tecla B: fabrica una **Oficina** y colócala en tu base. Con **clic derecho** sobre ella se abre el mismo panel.

**Receta de la Oficina**

| | | |
|---|---|---|
| Libro | Esmeralda | Libro |
| Tablas | Mesa de trabajo | Tablas |
| Tablas | Tablas | Tablas |

(Cualquier tipo de tablas vale.)

---

## Contratar becarios y precios

- Puedes tener **hasta 5 becarios** a la vez.
- El precio sube con cada puesto y se paga en **esmeraldas** del inventario:

| Becario n.º | Coste |
|---|---|
| 1 | 5 |
| 2 | 10 |
| 3 | 20 |
| 4 | 40 |
| 5 | 80 |

- **Creativo:** contratar es gratis.
- Si **despides** a uno, su puesto queda libre, pero el siguiente que contrates vuelve a costar lo que cuesta ese puesto.
- Dos becarios tuyos no pueden llamarse igual. El nombre tiene un máximo de caracteres.

---

## El panel (tecla B)

Es el centro de control. Desde aquí puedes:

- Ver **toda tu plantilla** (hasta 5) con el estado de cada becario.
- **Contratar** a uno nuevo.
- Abrir las **órdenes** de uno concreto (**Órdenes…**), o **despedirlo**.
- Pedir una **brújula** que apunta siempre a ese becario.
- Nombrar **jefes** de un área y organizar sus **equipos** (ver [Jefes y equipos](#jefes-y-equipos)).
- Aceptar o rechazar las peticiones de contratación que te hagan los jefes.

---

## El menú de un becario

Clic derecho sobre un becario (o **Órdenes…** en el panel):

| Botón | Qué hace |
|---|---|
| **Sígueme** | Te sigue a todas partes. |
| **Quieto** | Se queda donde está. |
| **Ven aquí** | Viene corriendo hasta ti. |
| **Parar** | Cancela el trabajo que esté haciendo. |
| **Recoger** | Recoge los objetos que haya por el suelo cerca. |
| **Dame todo** | Te entrega todo lo que lleva encima. |
| **Minar…** | Elige material y cantidad. |
| **Talar** | Tala árboles. |
| **Fabricar…** | Cuadrícula de objetos para fabricar. |
| **Construir** | Casa pequeña, casa grande o portal al Nether. |
| **Combate** | Pasivo, Defensa, Escolta o Guardia. |

---

## Minar y talar

Con **Minar…** eliges **qué** y **cuánto** (1 a 64). Materiales disponibles:

> madera · piedra · tierra · arena · carbón · hierro · cobre · oro · redstone · lapislázuli · diamante · esmeralda · obsidiana · grava · cuarzo · netherita

Cómo trabaja:

- Busca el material cerca. Si **no lo hay a la vista**, sale de **expedición**: baja por escaleras que él mismo excava, abre túneles, **pone antorchas** para iluminarlos y **vuelve por su rastro** cuando termina.
- Cada material tiene su **altura típica** (por ejemplo, diamante y redstone abajo del todo; hierro y cobre más arriba) y el becario baja hasta la zona adecuada.
- Va **guardando lo que consigue** en su inventario, y cuando termina te avisa. Pídele **Dame todo** para recibirlo.
- **Talar** funciona igual con árboles.
- Pica más rápido si lleva una herramienta adecuada; sin ella, pica a mano (más lento).

---

## Fabricar

**Fabricar…** abre una cuadrícula con unos 50 objetos (mesa de trabajo, horno, cofre, antorchas, escaleras, puertas, herramientas de madera/piedra/hierro/diamante, armaduras de hierro y diamante, escudo, cubo, mechero, carriles, vagoneta, etc.).

1. Elige la **cantidad** (el botón rota entre 1, 2, 4, 8, 16, 32 y 64).
2. Pulsa el objeto.

El becario hace el resto:

- Calcula la **receta completa** y los materiales que le faltan.
- **Los consigue él solo**: tala, mina, funde minerales en un horno, etc.
- Si hace falta, **coloca una mesa de trabajo o un horno** y fabrica.
- Cuando acaba, te lo dice y se queda con el objeto en su inventario (pídelo con **Dame todo**).

Si el becario es **jefe de Fabricación**, reparte el encargo entre los miembros de su equipo.

---

## Construir

Desde **Construir** eliges una obra. Se levanta **delante de ti y mirando hacia ti**.

| Obra | Tamaño / contenido |
|---|---|
| Casa pequeña | 5 × 5 |
| Casa grande | 7 × 7 |
| Portal al Nether | Marco de obsidiana y encendido |

El becario **junta él mismo los materiales** (piedra, madera, puerta, antorcha; obsidiana y mechero para el portal) y luego construye.

---

## Combate

Menú **Combate**:

| Modo | Comportamiento |
|---|---|
| **Pasivo** | No ataca. |
| **Defensa** | Te defiende y ataca a lo que tú golpeas o a lo que te ataca. |
| **Escolta** | Como Defensa, y además ataca a cualquier monstruo que vea dentro de un radio a tu alrededor. |
| **Guardia** | Protege una **zona fija** y no se mueve de ella. |

Además, sea cual sea el modo:

- Se **equipa la mejor arma** que lleve.
- Se **cubre con el escudo** (si tiene uno) ante flechas, golpes y explosiones: creepers a punto de explotar y TNT encendido.
- **Come** cuando está herido, si lleva comida.

---

## Jefes y equipos

Un becario puede ser **jefe** de un área y tener a otros en su **equipo**. Se gestiona en el **panel**.

| Cargo | Qué hace el jefe |
|---|---|
| **Minería** | Reparte entre su equipo las órdenes de minar. |
| **Tala** | Reparte entre su equipo las órdenes de talar. |
| **Fabricación** | Reparte los encargos de fabricar. |
| **Guardia** | Pone a todo su equipo en su mismo modo de combate; en modo guardia, cada uno ocupa un punto distinto de la zona. |

Cómo se usa:

- Pulsa el botón de **cargo** de un becario para **cambiar el área** (va rotando entre las disponibles).
- Añade becarios a su **equipo** desde el panel. Los miembros **siguen al jefe**.
- **Quitar** retira el cargo. Su equipo pasa al jefe de arriba, o queda libre si no hay ninguno.
- Los jefes pueden estar en el equipo de **otro jefe** (jerarquía de varios niveles). Las órdenes de un área bajan por todos los niveles de la misma área.
- Cuando se cambia el área de un jefe, **conserva su equipo**.
- Si un jefe va corto de gente para un encargo grande, **te pide contratar a alguien** con un mensaje en el chat (**[Aceptar]** / **[Rechazar]**) o en el panel.

---

## Seguirte por portales

Si cruzas un portal del Nether estando el becario cerca y **sin trabajo**, **va detrás de ti**: busca el portal, se coloca delante de él y entra.

- Solo cruza portales en ese caso: si está trabajando junto a uno, no se cuela por accidente.
- Mientras estás en otra dimensión, **sigue cargado** y esperando donde estaba, siempre que hayas pasado cerca de él antes de irte.
- No se queda sentado ni parado al otro lado del portal: continúa siguiéndote.
- Si el portal sale al borde de un precipicio, el becario espera en lugar de saltar al vacío.

---

## Inventario, objetos y brújula

- Cada becario tiene su propio **inventario** (ábrelo desde su menú o con `/becario inventario`).
- **Darle algo:** *Mayús + clic derecho* con el objeto en la mano, o **tirárselo** (tecla Q) estando cerca.
- **Recibir lo que lleva:** **Dame todo**.
- **Brújula:** desde el panel te da una brújula que **siempre apunta al becario**, útil si se ha ido lejos a minar.

---

## Si muere, si se atasca

- **Si muere**, deja **todo lo que llevaba** (inventario y equipo) en un **cofre** donde cayó, y te envía un mensaje con las **coordenadas y la dimensión**. Si no cabe todo en un cofre, usa un segundo cofre encima; lo que sobre cae al suelo. El cofre nunca se pone sobre lava ni por debajo del mundo.
- **Si se queda encerrado** dentro de un bloque, **pica para salir** (no rompe obsidiana ni bloques irrompibles).
- Si no encuentra camino hasta ti, te avisa en lugar de teletransportarse.
- Si vuelas o estás en el aire sobre un hueco, **espera** en el borde en vez de tirarse detrás de ti.

---

## Comandos

Todo se puede hacer con las pantallas, pero existen comandos `/becario …` (útiles para scripts o servidores).

| Comando | Para qué |
|---|---|
| `panel` | Abre el panel. |
| `lista` | Lista tus becarios (n/5). |
| `elegir <nombre>` | Elige con cuál hablas. |
| `contratar` | Abre la pantalla de contratar. |
| `invocar "<nombre>" <skin>` | Crea un becario con nombre y skin (mismas reglas y precio que contratar). |
| `sigue` · `quieto` · `ven` · `para` · `recoge` · `dame` | Órdenes básicas. |
| `inventario` · `estado` · `donde` · `brujula` | Información y brújula. |
| `mina <material> [n]` · `tala [n]` | Trabajo. |
| `fabrica <objeto> [n]` | Fabricar (el objeto, por su id: `minecraft:chest`). |
| `construye pequena` · `grande` · `portal` | Obras. |
| `modo pasivo` · `defensa` · `escolta [radio]` · `guardia [radio]` | Combate. |
| `peticion aceptar` · `rechazar` | Responder a la petición de un jefe. |
| `despedir` | Despide al becario elegido. |

---

## Preguntas frecuentes

**¿Un becario puede teletransportarse hasta mí?**
No. Siempre viene andando. Si no encuentra camino, te avisa.

**¿Mis becarios pueden hacerle caso a otro jugador?**
No. Solo obedecen a su dueño.

**¿Qué pasa si me desconecto?**
Tus becarios se guardan con el mundo y siguen asignados a ti cuando vuelvas.

**¿Por qué no puedo entrar al servidor?**
Casi siempre es porque el jar del cliente y el del servidor no son la misma versión, o falta Fabric API. Instala el mismo jar en los dos.

**Mi becario no encuentra el mineral que le pido.**
Sale de expedición bajando y abriendo túneles. Si lleva mucho rato sin éxito, pídele que pare y llévalo a una zona donde haya ese material.

**¿Puedo añadir mis propias skins?**
Sí, al compilar: pon un PNG de 64×64 en la carpeta `skins/` y compila. Aparece en la lista al contratar. El catálogo va dentro del jar, así que servidor y clientes deben tener el mismo.

---

## Desarrollo y compilación

Proyecto Fabric con **Loom** (Mojang mappings), Gradle 9.7 y Java 25.

```bash
./gradlew build        # el .jar sale en build/libs/
./gradlew runClient    # probar en local
./gradlew runServer    # servidor de pruebas
```

Estructura principal (`src/main/java/com/jorgesox/becarios/`):

| Paquete | Contenido |
|---|---|
| `entity` | El becario, su registro por jugador, áreas, modos de combate y objetivos de movimiento (seguir, portales, volver al puesto…). |
| `task` | Minar y talar, grupos de bloques, expediciones y alumbrado de túneles. |
| `craft` | Planificador de recetas y fabricación. |
| `build` | Obras (casas, portal). |
| `command` | Comandos `/becario` y el panel. |
| `network` | Paquetes cliente ↔ servidor. |
| `block` / `menu` | Bloque Oficina e inventario del becario. |

El código de cliente (pantallas, tecla B) está en `src/client/`.

---

## Licencia

[CC0 1.0](LICENSE): dominio público.
