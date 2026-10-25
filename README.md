# Becarios de Notch

Mod de Fabric para Minecraft Java **26.3**: contrata becarios que trabajan para ti. Minan, talan, fabrican, construyen, te defienden y te siguen a donde vayas (también por portales). Cada jugador tiene los suyos y solo él les da órdenes.

## Instalación

- Hace falta **en el servidor y en cada jugador** (añade un bloque, una criatura y pantallas propias).
- Requisitos: Fabric Loader 0.19.5 o superior y Fabric API 0.163.0+26.3.
- Copia `becarios-de-notch-<versión>.jar` en la carpeta `mods`.

## Empezar

| Qué | Cómo |
|---|---|
| Abrir el panel de becarios | Tecla **B** (configurable en Controles), o clic derecho en una **Oficina** |
| Fabricar una Oficina | Libro · Esmeralda · Libro / Tablas · Mesa de trabajo · Tablas / Tablas · Tablas · Tablas |
| Contratar | Botón **Contratar** del panel: eliges nombre y aspecto |
| Hablar con uno | Clic derecho sobre él (o **Órdenes…** en el panel) |
| Darle algo | Mayús + clic derecho con el objeto en la mano, o tirárselo (Q) |

**Precio:** hasta 5 becarios por jugador. Cuestan 5, 10, 20, 40 y 80 esmeraldas (en creativo, gratis). Si despides a uno, ese hueco vuelve a costar lo suyo.

## Qué saben hacer (menú del becario)

- **Sígueme / Quieto / Ven aquí / Parar / Recoger / Dame todo.**
- **Minar** el material que elijas (madera, piedra, carbón, hierro, oro, diamante, obsidiana, grava, cuarzo, netherita…) en la cantidad que digas. Si no lo hay cerca, sale de expedición: baja por escaleras, ilumina los túneles con antorchas y vuelve por su rastro.
- **Talar** árboles.
- **Fabricar…**: eliges el objeto en una cuadrícula (herramientas, armadura, cofres, horno, antorchas, cubo, escudo…). Consigue él los materiales que le falten, coloca mesa o horno si hace falta, y lo fabrica.
- **Obras**: casa pequeña (5x5), casa grande (7x7) o **portal al Nether**, delante de ti y mirando hacia ti. Junta él los materiales: piedra, madera, puerta, antorcha, obsidiana y mechero.
- **Combate**:
  - Modos: Pasivo, Defensa (te defiende y ataca lo que tú golpeas), Escolta (vigila a tu alrededor) y Guardia (protege una zona).
  - Se equipa la mejor arma que lleve.
  - **Se cubre con el escudo** de flechas, golpes y explosiones (creepers, TNT).
  - Come si está herido.

## Equipos y jefes

En el panel puedes nombrar a un becario **jefe** de un área y meter a otros en su equipo:

| Jefe de | Qué hace |
|---|---|
| Minería / Tala | Reparte entre su equipo las órdenes de minar o talar |
| Fabricación | Reparte los encargos de fabricar |
| Guardia | Pone a su equipo en su mismo modo de combate (en modo guardia, cada uno en un punto de la zona) |

Más cosas de los equipos:
- Los de su equipo le siguen a él.
- Si va justo de gente, **te pide contratar a alguien** ([Aceptar] / [Rechazar] en el chat o en el panel).
- Un jefe puede estar en el equipo de otro jefe (jerarquía). Los encargos bajan por todos los niveles de la misma área.

Cambiar el cargo de un jefe:
- **Clic** en el cargo cambia el área, y el jefe conserva su equipo.
- **Mayús + clic** le quita el cargo, y su equipo pasa al jefe de arriba (o queda libre).

## Otras cosas

- **Brújula**: desde el panel te da una brújula que siempre apunta a ese becario.
- **No se teletransporta nunca**: viene andando o corriendo. Si no encuentra camino, te lo dice.
- **Portales**: si cruzas un portal estando él cerca y sin trabajo, va detrás de ti.
- **Si muere**: deja sus cosas en un cofre donde ha caído y te dice las coordenadas.
- **Si se queda encerrado** en un bloque, pica para salir.

## Comandos (opcionales)

Todo se puede hacer desde las pantallas, pero existen `/becario …`:

| Comando | Para qué |
|---|---|
| `panel`, `lista`, `elegir <nombre>`, `contratar` | Gestionar la plantilla |
| `sigue`, `quieto`, `ven`, `para`, `recoge`, `dame`, `inventario`, `estado`, `donde`, `brujula` | Órdenes básicas |
| `mina <material> [n]`, `tala [n]`, `fabrica <objeto> [n]` | Trabajo |
| `construye pequena / grande / portal` | Obras |
| `modo pasivo / defensa / escolta [radio] / guardia [radio]` | Combate |
| `despedir` | Despedirlo |

## Desarrollo

```
./gradlew build        # el .jar sale en build/libs/
./gradlew runClient    # probar en local
```
