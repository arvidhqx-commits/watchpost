# WatchPost — Nischenbeleg und Positionierung

Stand 02.09.2026. Alle Zahlen an der Spiget- und Modrinth-API geprüft; das fremde Jar selbst
heruntergeladen und auf beiden lokalen Paper-Servern gestartet.

## Wie der Kandidat gefunden wurde
Aus `radar/spigot-orphans.json` (435 Kandidaten des SpigotMC-Scanners), Rang 114.
Vorher gekillt aus derselben Liste: **Armor Stand Tools** (302k DL / 27,7 Monate still — aber
`easyarmorstands` ist aktiv, unterstützt 26.2 und wurde am 07.08.2026 ausgeliefert),
**ServerListPlus** (260k — MiniMOTD ist mit 242k auf Modrinth aktiv, 30.06.2026) und
**StackMob** (228k — RoseStacker aktiv, 22.05.2026).

## Der Orphan
**Security Camera Plugin - CCTV** (Streampy/Timdecoole123/Tanguygab), SpigotMC-Ressource 60310 —
**197.532 Downloads**, Bewertung **4,2** aus 82 Wertungen, letzte Auslieferung **17.11.2024**
(21,5 Monate). Eigene Versionstabelle des Projekts: „MC 1.20.6–1.21.3 = V9.0.7" — es beansprucht
also selbst nicht, auf 1.21.11 oder 26.2 zu laufen.

## Pflichtfilter 1: läuft das Original noch? — **Auf BEIDEN aktuellen Versionen nein**
Jar 9.0.7 (153 KB) von Spiget geladen und einzeln gestartet:

| Server | Ergebnis |
|---|---|
| Paper 1.21.11 | **stürzt beim Aktivieren ab** |
| Paper 26.2 | **stürzt beim Aktivieren ab** |

```
java.lang.NoSuchMethodError:
  'com.mojang.authlib.properties.PropertyMap com.mojang.authlib.GameProfile.getProperties()'
    at io.github.tanguygab.cctv.utils.Heads.createSkull(Heads.java:53)
    at io.github.tanguygab.cctv.utils.Heads.<init>(Heads.java:42)
    at io.github.tanguygab.cctv.CCTV.onEnable(CCTV.java:74)
```

Nicht die Kameralogik ist gestorben, sondern ein **dekorativer Spielerkopf**, gebaut über
`com.mojang.authlib` — eine Mojang-interne Klasse außerhalb der Bukkit-API. Die Portfolio-These
im Stacktrace, zum zweiten Mal nach ChestSort und PocketMap.

## Pflichtfilter 2: gibt es einen aktiven Nachfolger? — **Nein, und das ist der stärkste Befund bisher**
Gesucht mit den generischsten Nischenbegriffen (Regel vom 31.08.), auf Modrinth **und** SpigotMC:
„camera", „cctv", „security camera", „surveillance".

| Anbieter | Plattform | Downloads | letzte Auslieferung |
|---|---|---|---|
| Security Camera Plugin - CCTV | SpigotMC | 197.532 | 17.11.2024 (tot, s. o.) |
| Security Camera System | SpigotMC | 60.430 | 24.07.2018 |
| BetterCamera's | SpigotMC | 42.198 | 02.04.2018 |
| Cameras | SpigotMC | 14.237 | 19.04.2021 |
| PowerCamera (Kinofahrten, andere Aufgabe) | SpigotMC | 13.137 | 02.06.2025 |
| security-cameras | Modrinth | 1.861 | 25.10.2023 |
| cameraplugin | Modrinth | 470 | 13.07.2026 |
| CCTVCamera | SpigotMC | 312 | 26.08.2024 |

Der stärkste **aktiv gepflegte** Anbieter der Nische hat **470 Downloads**. Faktor gegenüber dem
Orphan: **420**. Zum Vergleich: NeatSort 21, PocketMap ähnlich. Es gibt in dieser Nische derzeit
schlicht kein gepflegtes Angebot.

## Pflichtfilter 3: besteht der Grund fort, aus dem es das Projekt gab?
Ja. Vanilla-Minecraft hat nichts Vergleichbares, und die Zielgruppe (Rollenspiel-, Stadt- und
Gefängnisserver) existiert unverändert. Die 197k Downloads sind über Jahre gewachsen.

## Positionierung
Nicht „mehr Funktionen", sondern **der Ausstieg**. Der teuerste Fehler eines Kamera-Plugins ist
ein Spieler, der nach einem Neustart im Zuschauermodus in der Wand steckt. WatchPost schreibt den
Rückweg auf die Platte, **bevor** es den Spieler anfasst, und deckt acht Wege aus der Kamera
einzeln ab (siehe README). Dazu: keine Entities, keine Spielerköpfe, kein authlib — genau die
drei Dinge, an denen der Vorgänger gestorben ist.

## Ehrlich benannte Grenze
Der Zuschauermodus sieht durch Wände. Das gilt für **jedes** serverseitige Kamera-Plugin und wird
im Listing nicht verschwiegen, sondern erklärt: wer Kameras setzen darf, ist eine
Vertrauensentscheidung.

## Prüfstand
44 Zusicherungen gegen die **ausgelieferten** Klassen (über WatchPosts eigenen Classloader),
grün auf Paper 1.21.11 **und** 26.2, im gemeinsamen Lauf mit 15 weiteren Plugins, 0 Errors.
Die Prüfung selbst wurde durch einen Mutationstest gegengeprüft: zwei absichtlich eingebaute
Fehler in der Rückfahrkarte (Spielmodus nicht geschrieben, X-Koordinate um 1 verschoben) wurden
von genau den beiden zuständigen Zusicherungen gefangen.
