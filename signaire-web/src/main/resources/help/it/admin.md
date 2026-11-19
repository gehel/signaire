# Amministrazione

Gli amministratori sono gli utenti indicati nella configurazione (`signaire.accounts.admins`). Vedono
«Amministrazione» nell'intestazione.

## Modificare i segni

- **Nuovo segno**: per aggiungere un segno che SignSuisse non ha.
- Sulla pagina di un segno: **Modifica il segno** ed **Elimina il segno**.
- **Segni modificati** elenca le modifiche; «Annulla» ripristina il segno di SignSuisse.

I dati dei segni possono essere ricaricati in qualsiasi momento: le modifiche dell'amministrazione sono conservate a
parte e applicate di nuovo dopo ogni caricamento.

## Bundle

I segni arrivano in **bundle**, un file `.zip` per categoria di SignSuisse. La pagina **Bundle** li elenca, ne
accetta uno nuovo (sostituisce quello della stessa categoria), ricarica i bundle nuovi o modificati, o reindicizza
tutto (dopo un aggiornamento di Signaire che cambia l'indice).

## Sincronizzazione con SignSuisse

Scegli categorie e lingue dei segni (nessuna selezionata: tutte), e i media da scaricare: video e immagini, solo
video, solo immagini o nessuno. I media non scaricati sono mostrati da SignSuisse, ma mancano negli export e senza
connessione. La sincronizzazione gira in background; ogni segno è consultabile appena estratto (i suoi
media mostrati da SignSuisse fino alla fine della sua categoria).

Signaire risparmia SignSuisse: le richieste sono distanziate di almeno mezzo secondo, e i segni già estratti vengono
ripresi dai pacchetti esistenti invece di essere riletti; seleziona **Rileggere i segni già estratti** per ottenere le
correzioni fatte nel frattempo su SignSuisse. I segni trovati vengono letti, e i loro media scaricati, mentre la
ricerca continua. La pagina mostra a che punto è la categoria in corso (ricerche, segni, media: fatti, rimanenti, in
errore), cosa si sta facendo, una stima del tempo rimanente e le categorie rimanenti. Senza media, una categoria
richiede da qualche minuto a mezz'ora; con i media, molto di più. Un media in errore resta mostrato da SignSuisse; la
sincronizzazione successiva lo riprova.

## Contatto per SignSuisse

Ogni richiesta a SignSuisse dice che viene da Signaire, con un link al progetto. Aggiungi il tuo indirizzo e-mail: se
Signaire sovraccarica il sito, SignSuisse potrà scriverti invece di bloccare i tuoi download. Scrivilo nel file
`application.properties` della cartella di configurazione di Signaire (`~/.config/signaire/` su Linux), letto anche
dalla riga di comando:

```properties
signaire.signsuisse.contact=tu@esempio.org
```

**Privacy**: l'indirizzo viene inviato a SignSuisse con ogni richiesta, in chiaro nell'intestazione `User-Agent`.
SignSuisse (la Federazione Svizzera dei Sordi) e il suo hosting lo vedranno nei loro log e lo conserveranno secondo
le loro regole; chiunque veda passare queste richieste può leggerlo. Usa un indirizzo che accetti di condividere, per
esempio uno dedicato. `signaire.signsuisse.user-agent` sostituisce l'intera intestazione.

## Richieste di segni

Per ogni richiesta in attesa: **Cerca su SignSuisse** e **importa** il segno giusto (o **collegalo** se è già nel
lessico), **crea il segno** tu stesso, o **rifiuta** la richiesta. L'utente vede il risultato nelle sue richieste.
