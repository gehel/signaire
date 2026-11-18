# Applicazione autonoma

L'applicazione autonoma fa funzionare Signaire sul vostro computer, senza server: nessuna banca dati né motore di
ricerca da installare. Avviatela con un doppio clic (`signaire-web-…-standalone.jar` con Java 27, o il programma
Signaire, che porta il proprio Java): apre Signaire nel vostro browser, all'indirizzo <http://localhost:8180>. Se Signaire è già in
funzione, un nuovo avvio apre semplicemente il browser.

Il programma non è firmato: al primo avvio, macOS chiede di aprirlo con un clic destro e poi «Apri», e Windows
di confermare in «Ulteriori informazioni».

## Primo avvio

Al primo avvio, Signaire chiede:

- **chi lo usa**: solo voi (nessun account né password: siete anche l'amministratore) o più persone (ognuna crea
  il proprio account; il primo, creato subito, è quello dell'amministratore);
- **i segni da scaricare** da SignSuisse: scegliete alcune categorie e lingue. Lo scaricamento dura a lungo e
  continua mentre usate Signaire; ne seguite l'avanzamento nell'amministrazione, da dove potrete anche scaricare
  altre categorie più tardi.

Solo questo computer può usare Signaire: non è accessibile dalla rete.

## Chiudere

«Chiudere Signaire», in alto nella pagina, ferma l'applicazione (per l'amministratore). Chiudere il browser non
basta.

## Dove sono i file

Sotto Linux, i bundle sono in `~/.local/share/signaire/bundles`, la banca dati e l'indice di ricerca in
`~/.local/state/signaire`, e le scelte del primo avvio in `~/.config/signaire/standalone.properties` (le cartelle
abituali sotto macOS e Windows). Cancellate quest'ultimo file per rifare le scelte del primo avvio.
