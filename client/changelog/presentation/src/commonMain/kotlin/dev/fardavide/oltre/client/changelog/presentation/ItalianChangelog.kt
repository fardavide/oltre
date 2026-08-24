package dev.fardavide.oltre.client.changelog.presentation

// The same releases, in Italian. Same order, same dates, same number of notes per
// release — asserted by `ChangelogTranslationTest`, which is what stands in for the exhaustive
// `when` the global catalogue uses.
//
// **Italian runs 10–15% longer than English and the budget does not move**, so the copy is what
// gives: a note here is written shorter rather than allowed to reach a fourth line. That is the
// design's own note about the cap binding the copy and not the layout.
//
// Names the game already translates are used as the game already translates them — *Cantiere*,
// *Miniera di Cristallo*, *Fabbrica Robotica*, *Propulsione* — because a changelog that renamed a
// facility would be the only place in the app it went by a different word.
object ItalianChangelog : ChangelogText {

    override val releases: List<Release> = listOf(
        release(
            "0.20.1", "2026-08-24", "Aprire il gioco svuota le notifiche",
            "Gli avvisi che hai già letto spariscono quando riapri la colonia.",
            "Quelli ancora da arrivare restano al loro posto.",
        ),
        release(
            "0.20.0", "2026-08-24", "Ora la galassia è al buio",
            "Una stella dove nessuno è stato è un punto di luce e nulla più.",
            "Una sonda mappa tutto ciò che è più vicino di dove va, e un'ora oltre.",
            "Parti da 61 sistemi su 250, e sono le tue navi a illuminare il resto.",
        ),
        release(
            "0.19.0", "2026-08-24", "Il gioco dice cosa è cambiato",
            "Ogni versione di Oltre, una pagina ciascuna, dalla più recente.",
            "Si apre da sé su una nuova versione e poi non più.",
            "Ogni pagina disegna il suo cielo dal numero di versione.",
        ),
        release(
            "0.18.0", "2026-08-23", "Il pulsante apre qualcosa",
            "Gli avvisi si chiedono per categoria invece che riga per riga.",
            "Consegna dice in quante notifiche arrivano le risposte.",
            "Le nuove colonie partono più chiare e più silenziose.",
        ),
        release(
            "0.17.1", "2026-08-23", "Il tuo nome prende tutta la riga",
            "La barra dell'esperienza è diventata il bordo inferiore della striscia.",
            "Il nome più lungo ora entra intero nella finestra più stretta.",
        ),
        release(
            "0.17.0", "2026-08-23", "La barra si riempie",
            "Tutto quello che finisci ora paga esperienza.",
            "Aprendo questa versione conti tutto ciò che hai fatto prima del livello.",
            "Un giorno vale circa il livello 3, una settimana 10, un mese 25.",
        ),
        release(
            "0.16.0", "2026-08-22", "Il gioco sa chi sta giocando",
            "Una striscia sopra le risorse porta il tuo segno, il nome e il livello.",
            "Sei Dead Reckoning, al livello 0.",
        ),
        release(
            "0.15.4", "2026-08-22", "Ogni avviso è uno che hai chiesto",
            "Una campanella accanto a Invia: toccala e quel volo lo dirà all'arrivo.",
            "Un volo che non hai chiesto non suona più.",
        ),
        release(
            "0.15.3", "2026-08-22", "Gli scafi dicono quando sono pronti",
            "Campanella accanto a Costruisci: una volta per l'ordine, due per scafo.",
            "Ogni scafo si chiede a parte.",
        ),
        release(
            "0.15.2", "2026-08-21", "Quattro crash e un conteggio",
            "Il cargo non è più offerto per un mondo che nessuna finestra raggiunge.",
            "Una flotta di cargo si conta di nuovo in stive.",
            "Italiano: il giacimento è maschile e una vena da uno concorda.",
        ),
        release(
            "0.15.1", "2026-08-21", "I mondi lontani non chiudono il gioco",
            "Un mondo irraggiungibile in un giorno lo dice e indica Propulsione.",
            "Una flotta di soli cargo si può inviare di nuovo.",
        ),
        release(
            "0.15.0", "2026-08-21", "La distanza costa, e un motore la ripaga",
            "Volare costa il doppio, quindi il bordo della mappa è fuori portata.",
            "Propulsione restituisce finestre ai mondi che le rifiutavano.",
            "Esplorare costa una nave, e lo Scout è la cosa più economica del gioco.",
        ),
        release(
            "0.14.0", "2026-08-19", "Il gioco parla italiano",
            "Ogni parola, su un telefono in italiano, senza niente da attivare.",
            "Anche i numeri, i mondi e la flotta sono in italiano.",
        ),
        release(
            "0.13.2", "2026-08-18", "I pulsanti non lampeggiano quadrati",
            "L'evidenziazione del tocco ora si ferma dove finisce il controllo.",
            "Cambiare scheda è un movimento invece che uno stacco.",
            "Niente va in ciclo e niente parte da solo.",
        ),
        release(
            "0.13.1", "2026-08-17", "Il foglio apre la flotta che riempie",
            "Oltre una certa taglia ogni scialuppa in più torna vuota.",
            "Tieni premuto − o + per far correre il conteggio.",
        ),
        release(
            "0.13.0", "2026-08-16", "La scheda Flotte ha una porta indietro",
            "Una riga per ogni mondo lavorato, con l'ultimo atterraggio in cima.",
            "Toccane una per inviare un'altra spedizione.",
            "Un mondo con la vena finita dice vuoto sulla riga.",
        ),
        release(
            "0.12.2", "2026-08-16", "L'adattamento non fa più a turno",
            "Ogni ramo ha la sua coda, così scala e tecnologia procedono insieme.",
        ),
        release(
            "0.12.1", "2026-08-16", "La barra sotto la mappa è tornata",
            "La mappa si piega nello spazio che resta invece di prenderlo per prima.",
        ),
        release(
            "0.12.0", "2026-08-15", "La mappa stellare arriva",
            "Duecentocinquanta stelle in dieci bande, su un solo schermo.",
            "Vicino sulla mappa è vicino nel gioco.",
            "Un tocco in su mostra le quattro galassie e quanto costano.",
        ),
        release(
            "0.11.3", "2026-08-15", "Una nuova colonia parte senza navi",
            "Il primo scafo è la prima cosa che compri invece di un regalo.",
        ),
        release(
            "0.11.2", "2026-08-15", "I mondi contengono quattro volte tanto",
            "Un mondo vicino porta 5.800 di metallo, uno pericoloso fino a 15.950.",
            "Una flotta svuota ancora un mondo in un giorno: servono quattro scafi.",
        ),
        release(
            "0.11.1", "2026-08-15", "Il registro apre il mondo che tocchi",
            "Apriva lo stesso slot del sistema su cui era rimasta la mappa.",
        ),
        release(
            "0.11.0", "2026-08-14", "La mappa ha luoghi, non indirizzi",
            "Ogni sistema e ogni mondo ha un nome, generato dal tuo seme.",
            "Un mondo esplorato ha un volto in cui ogni canale è un tratto vero.",
            "Dieci regioni per galassia, ognuna davvero diversa.",
        ),
        release(
            "0.10.1", "2026-08-14", "Ogni scafo costa uguale",
            "800 di metallo e 200 di cristallo fissi, a ogni profondità.",
            "A rendere cara una flotta è il cantiere, che ne costruisce uno alla volta.",
        ),
        release(
            "0.10.0", "2026-08-13", "I mondi si esauriscono",
            "Ogni mondo ha una vena finita, e una spedizione ne prende.",
            "Un mondo torna al cinque per cento al giorno.",
            "Prospezione: ogni scafo tira fuori di più da ogni mondo.",
        ),
        release(
            "0.9.0", "2026-08-13", "Le navi richiedono tempo",
            "Uno scafo va sullo scalo con un conto alla rovescia, e gli ordini fanno coda.",
            "Le navi costano dieci volte tanto.",
            "Ti viene detto quando uno scafo lascia il cantiere.",
        ),
        release(
            "0.8.0", "2026-08-12", "Puoi comprare navi",
            "Il Cantiere è un listino, e la scheda Flotte mostra cosa è fuori.",
            "Nessuna scheda dice più che qui non c'è niente.",
        ),
        release(
            "0.7.2", "2026-08-12", "I mondi pericolosi pagano di più",
            "Una spedizione porta a casa il triplo di prima.",
            "Il pericolo aggiunge un terzo alla stiva invece di toglierne un decimo.",
        ),
        release(
            "0.7.1", "2026-08-12", "Il foglio di invio è un vero foglio",
            "Copre la finestra, la maniglia trascina, e lo scorrimento è suo.",
        ),
        release(
            "0.7.0", "2026-08-12", "Puoi mandare una nave da qualche parte",
            "Scegli la risorsa, gli scafi e quanto manca al rientro.",
            "Un mondo su cui non puoi vivere vale comunque il viaggio.",
            "Una spedizione è gratis: il prezzo era lo scafo.",
        ),
        release(
            "0.6.0", "2026-08-11", "Ogni riga dice quanto vale",
            "Una riga sola: cosa ti dà il livello, e quando lo hai ripagato.",
            "Tocca una riga per aprire i conti dietro il verdetto.",
            "Niente cambia nel bilanciamento.",
        ),
        release(
            "0.5.2", "2026-08-11", "La Fabbrica di Naniti fa qualcosa",
            "È l'unica cosa che accorcia una costruzione profonda.",
            "Oltre il livello 18 ogni livello costa più attesa di quanta ne guadagni.",
            "Le prime due settimane restano identiche al minuto.",
        ),
        release(
            "0.5.1", "2026-08-11", "I tuoi vicini meritano uno sguardo",
            "Una nuova colonia nasce accanto a un posto quasi calpestabile.",
            "Le tre scale di adattamento aprono a Robotica 2 invece che 4.",
        ),
        release(
            "0.5.0", "2026-08-10", "Ogni avviso è uno che hai chiesto",
            "Tocca una campanella e il gioco ti dice quando atterra, o quando puoi.",
            "Più cose che finiscono insieme arrivano come un avviso solo.",
        ),
        release(
            "0.4.4", "2026-08-10", "Il cielo si inclina nel verso giusto",
            "Abbassa il bordo destro e le stelle vanno dove inclini.",
        ),
        release(
            "0.4.3", "2026-08-10", "L'inclinazione laterale risponde uguale",
            "Ruotare ora vale quanto inclinare, da qualsiasi posa tenga la mano.",
            "L'effetto non ha più un limite.",
            "Posa il telefono e il cielo si ferma.",
        ),
        release(
            "0.4.2", "2026-08-10", "Il cielo dietro il gioco si inclina",
            "Tre piani di stelle scorrono l'uno sull'altro mentre inclini il telefono.",
            "Spento del tutto se hai chiesto al telefono meno movimento.",
        ),
        release(
            "0.4.1", "2026-08-10", "La flotta è stata misurata",
            "Una spedizione riporta la metà di prima.",
            "Niente che puoi vedere o fare cambia in questa versione.",
        ),
        release(
            "0.4.0", "2026-08-10", "La colonia galleggia su un cielo",
            "Centouno stelle, e nessuna si muove da sola.",
            "Una riga in corso porta un quadrante invece di una barra.",
            "Il gioco ti dice cosa è successo mentre non c'eri.",
        ),
        release(
            "0.3.0", "2026-08-10", "Una flotta, sotto il gioco",
            "Le navi possono lavorare un mondo esplorato e riportare carico.",
            "Puoi lavorare un mondo su cui non potresti mai vivere.",
            "Nessuno schermo lo offre ancora.",
        ),
        release(
            "0.2.7", "2026-08-09", "La prima ora è un altro gioco",
            "I primi potenziamenti costano un decimo e finiscono in due minuti.",
            "Lo sconto finisce dove si apre la galassia.",
        ),
        release(
            "0.2.6", "2026-08-09", "Il menu di debug va tenuto premuto",
            "Saltare avanti e cancellare non sono più un tocco distratto.",
            "Il pannello è un vero foglio dal basso.",
        ),
        release(
            "0.2.5", "2026-08-09", "Un menu di debug",
            "Scuoti il telefono per saltare avanti o ricominciare la colonia.",
            "Arriva a tutti, così funziona anche su TestFlight.",
        ),
        release(
            "0.2.4", "2026-08-09", "Niente può traboccare in silenzio",
            "Ogni costo, durata e scorta si rifiuta di tornare negativo.",
            "Una colonia lasciata per anni non si rompe più al rientro.",
            "Le scale di adattamento entrano nello sconto iniziale.",
        ),
        release(
            "0.2.3", "2026-08-09", "Tutta l'apertura è scontata",
            "Tutto ciò che compri nei primi giorni costa un terzo al primo livello.",
            "Finisce dove si apre la galassia.",
            "La scheda Ricerca apre il primo giorno invece del secondo.",
        ),
        release(
            "0.2.2", "2026-08-09", "I lavori non superano più il reddito",
            "Costruire ora dura più o meno quanto guadagnarlo, a ogni profondità.",
            "Saltare la Fabbrica Robotica non costa più due giorni.",
        ),
        release(
            "0.2.1", "2026-08-09", "Oltre gira su Android",
            "La stessa colonia, ricerca e galassia, da Android 8.0 in su.",
            "Ogni versione è scaricabile, con l'APK sulla pagina della release.",
        ),
        release(
            "0.2.0", "2026-08-09", "Puoi mandare una sonda",
            "Sotto le orbite: quanto costa e quanto dura il volo fino a quella stella.",
            "I pulsanti ± non ci sono più: una banda mostra 250 sistemi insieme.",
            "Ogni costruzione ora dura quanto costa.",
        ),
        release(
            "0.1.2", "2026-08-09", "La galassia si può esplorare",
            "Una sonda vola per ore e ogni mondo attorno a quella stella è esplorato.",
            "Nessuno schermo offre ancora l'invio.",
        ),
        release(
            "0.1.1", "2026-08-08", "Il cristallo cresce di una volta e mezza",
            "30/h diventano 36/h al livello 1, e ogni livello sale di conseguenza.",
            "La Miniera di Cristallo non è più il peggior acquisto.",
        ),
        release(
            "0.1.0", "2026-08-08", "La barra lo dice in una riga in meno",
            "Ogni risorsa porta il suo colore come sfera, con il tasso accanto.",
            "Sei rettangoli identici diventano un primo piano e uno sfondo.",
            "Ci sono stelle dietro il gioco.",
        ),
        release(
            "0.0.18", "2026-08-08", "Puoi comprare una scala di adattamento",
            "Termica, Gravitica e Atmosferica sono in vendita sotto le tecnologie.",
            "Tocca la tecnologia su un mondo bloccato per andare a comprarla.",
            "Una scala mostra la banda che allarga.",
        ),
        release(
            "0.0.17", "2026-08-07", "Tre scale, sotto il gioco",
            "Ogni livello allarga la banda di tolleranza sul proprio asse.",
            "Nessuno schermo le vende ancora.",
        ),
        release(
            "0.0.16", "2026-08-07", "Un mondo bloccato dice quanto vale",
            "Il rendimento sta accanto al verdetto, così un mondo si può prezzare.",
            "Ogni riga conta le bande che fallisce.",
        ),
        release(
            "0.0.15", "2026-08-07", "La galassia esiste",
            "Quattro galassie da 250 sistemi, da un solo numero salvato con te.",
            "Un mondo facile è un mondo povero.",
            "Un mondo su cui non puoi vivere dice cosa lo cambierebbe.",
        ),
        release(
            "0.0.14", "2026-08-07", "Scritto una volta invece che due",
            "Niente cambia per chi gioca: ogni schermo disegna quel che disegnava.",
            "Due copie di come si scrive un prezzo sono due cose che divergono.",
        ),
        release(
            "0.0.13", "2026-08-06", "La ricerca è giocabile",
            "Tre tecnologie, un progetto alla volta, e ogni settimana un'altra risposta.",
            "Le miniere dicono quando vanno a metà potenza.",
            "Le scorte ora ti seguono in tutto il gioco.",
        ),
        release(
            "0.0.11", "2026-08-06", "Il gioco ha cinque destinazioni",
            "Colonia, Ricerca, Cantiere, Galassia e Flotte, dal primo avvio.",
            "Le quattro non ancora pronte dicono cosa ci sarà.",
        ),
        release(
            "0.0.10", "2026-08-06", "Il gioco ti dice quando tornare",
            "Ogni costruzione e ogni flotta prenota un avviso all'istante esatto.",
            "La versione su TestFlight è quella nel repository.",
        ),
        release(
            "0.0.9", "2026-08-06", "Una vera app per iPad",
            "Riempie lo schermo, si ridimensiona e funziona in Split View.",
            "Oltre la larghezza di un telefono il contenuto si ferma e si centra.",
        ),
        release(
            "0.0.8", "2026-08-06", "I potenziamenti vanno in parallelo",
            "Ogni struttura costruisce per conto suo, con conto alla rovescia e barra.",
            "L'economia è stata riscalata a numeri che stanno in testa.",
            "Le colonie da 0.0.7 ricominciano.",
        ),
        release(
            "0.0.7", "2026-08-06", "La colonia sopravvive alla chiusura",
            "Livelli, scorte, lavori e flotte tornano, e le ore vengono accreditate.",
            "Un salvataggio corrotto inizia una colonia nuova invece di bloccarsi.",
        ),
        release(
            "0.0.6", "2026-08-06", "Le flotte di ritorno si vedono",
            "Una striscia ambra porta la provenienza e un conto alla rovescia.",
            "Il carico entra nei depositi all'atterraggio.",
        ),
        release(
            "0.0.5", "2026-08-06", "La schermata Colonia è giocabile",
            "Ogni struttura elenca livello, costo e durata.",
            "La risorsa che ti manca diventa rossa.",
        ),
        release(
            "0.0.4", "2026-08-06", "Oltre ha un volto",
            "Il lembo illuminato di un pianeta e una traiettoria che lo supera.",
        ),
        release(
            "0.0.3", "2026-08-05", "L'economia è reale",
            "Sei edifici, una coda di costruzione, e l'energia che frena le miniere.",
            "Il deposito ha un tetto e il simulatore corre una settimana in un attimo.",
        ),
        release(
            "0.0.2", "2026-08-05", "Il metallo cresce in tempo reale",
            "La prima fetta verticale attraverso ogni strato del gioco.",
        ),
        release(
            "0.0.1", "2026-08-05", "Il primo commit",
            "Un monorepo, la CI, e un ramo che deve essere revisionato.",
        ),
    )
}
