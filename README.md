# Alla pompa — prezzi carburanti delle province toscane

App Android che legge gli open data del MIMIT (Osservaprezzi Carburanti) e mostra
prezzi, classifiche per bandiera e andamento nel tempo, limitata alle dieci province toscane.

## Perché un'app e non un sito

Dal **16 ottobre 2026** il server del MIMIT risponde soltanto a indirizzi IP italiani:
da un server all'estero la connessione va in timeout senza nemmeno un errore. Questo
taglia fuori Netlify, le GitHub Actions, Cloudflare e ogni proxy CORS pubblico.

Dentro l'app la richiesta parte dallo smartphone, che in Italia un IP italiano ce l'ha.
Resta il CORS, ma quello è una regola del browser: qui le chiamate al ministero non
passano dalla WebView, le esegue `MainActivity.java` con `HttpURLConnection` e le
rimette nella pagina aggiungendo `Access-Control-Allow-Origin`.

Se apri l'app fuori dall'Italia il ministero non risponde e si ripiega
automaticamente su un archivio pubblico su GitHub, che però è fermo alla stessa data
del blocco.

## Compilare

Non serve Android Studio. Carica il repository su GitHub: la action in
`.github/workflows/apk.yml` compila a ogni push su `main` e pubblica l'APK in due posti.

- **Actions → ultima esecuzione → Artifacts**: `alla-pompa-apk`
- **Releases → tag `ultima`**: link di download che non cambia mai, comodo dal telefono

In locale, se ti serve:

```bash
cd android
gradle assembleDebug          # oppure ./gradlew se aggiungi il wrapper
```

L'APK esce in `android/app/build/outputs/apk/`.

## Installare

L'APK è firmato con la chiave di debug, quindi basta il sideload: apri il file dal
telefono e concedi «installa app sconosciute» al browser o al gestore file.

Per pubblicarlo sul Play Store servirebbe una chiave di release vera: si genera con
`keytool`, si mettono i quattro segreti nel repository e si sostituisce
`signingConfig signingConfigs.debug` in `app/build.gradle`.

## Struttura

```
android/app/src/main/
  java/it/gabriele/allapompa/MainActivity.java   WebView + richieste native al MIMIT
  assets/index.html                              tutta l'applicazione
  res/mipmap-*/                                  icone, legacy e adaptive
.github/workflows/apk.yml                        build automatica
```

Per modificare l'app si tocca solo `assets/index.html`: è la stessa pagina della
versione web, con in più la sorgente diretta del ministero e lo storico salvato in
`localStorage` a ogni apertura.

## Dati

Ministero delle Imprese e del Made in Italy — Osservaprezzi Carburanti, licenza IODL 2.0.
I prezzi sono quelli in vigore alle 8 del mattino comunicati dai gestori: si aggiornano
una volta al giorno. App non ufficiale, non affiliata al MIMIT né ai marchi citati.
