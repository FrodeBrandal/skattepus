# skattepus

Test-/utredingsapplikasjon for oppkobling mot Skatteetaten si API-integrasjon
via Maskinporten. Formalet er å verifisere at vi har tilgang (rettigheitspakker
+ scope) og at vi kan hente data frå dei einskilde DELING-api-a, forut for ei
eventuelt meir permanent integrasjon mot Frivilligheitsstotte/Stiftelsestilsyn.

Teknisk: Spring Boot 4.1.x, Java 17, Maven. Maskinporten-flyt via
`no.ks.fiks:maskinporten-client` — same klient og oppsettsmønster som
anlegg-common-svarut brukar.

## Verksemdsmode

1. Programmet signerer ein client-assertion med **var eigen private nøkkel**
   (JWT, RS256, `keyID` = KEYID fra Maskinporten-portalen).
2. KS FIKN-klienten bytter assertionen mot ein access_token hjå Maskinporten
   (test: `https://test.maskinporten.no/token`), eitt token per scope, cacha
   til det nærmar seg utløp.
3. Testkontrollaren kallar Skatteetaten sitt API med `Authorization: Bearer <token>`
   og returnerer status + rå JSON + Korrelasjonsid.

Ingen data blir lagra — dette er reine probe-kall.

## Krav for a fa gyldig token

| Krav | Kor fran |
|---|---|
| Privatnøkkel (PEM, PKCS#8) | `src/main/resources/keys/maskinporten-test-private.pem` — **ikkje i git**, holdast lokalt |
| Public key registrert i Maskinporten-portalen | gir oss ein **KEYID** (fingeravtrykk) |
| Client-id | Maskinporten-portalen (var test-klient) |
| Tildelte scope | Skatteetaten tildeler per verksemd — sjå `rettighetspakke-innsyn.skatteetaten.no` |

Noverande status: client-id, privatnøkkel og KEYID er på plass (token-signering
blir godteken av Maskinporten). Att står å få **tildelt scopene til klienten** —
foreløpig svarar Maskinporten «Consumer has not been granted access to the
scope skatteetaten:mvamelding». Test-scope utan `/test`-suffiks er det som gjeld
(`/test`-varianten blir avvist som ugyldig).

## Oppsett lokalt

1. Plasser privatnøkkelen som `src/main/resources/keys/maskinporten-test-private.pem`
   (eller peik ein annan filsti via `skattepus.maskinporten.private-key-file`).
2. Lag `src/main/resources/keys/secret.yml` (allereie git-ignorert) med:
   ```yaml
   skattepus:
     maskinporten:
       client-id: <client-id fra Maskinporten-portalen>
       key-id: <KEYID fra portalen>
       scope: skatteetaten:mvamelding   # brukast berre av /maskinporten/token
   ```
   Fila lastast via `spring.config.import` i `application.properties` og
   **eig alle hemmelegheiter** — ingen secret-verdien skal stå i properties-fila.
3. Køyr: `./mvnw spring-boot:run` (porter 8080).

## Konfigurerte API

Base-URL og scope per API ligg i `application.properties` under
`skattepus.skatteetaten.apis.*`. Test-miljo brukar vertsnavn pa forma
`<api>.api.skatteetaten-test.no` (prod: `.api.skatteetaten.no`).

| Navn | Scope | Sti |
|---|---|---|
| `aksjebeholdning` | `skatteetaten:aksjebeholdning` | `/person/{rettighetspakke}/{kalenderaar}/{ident}` (også `/virksomhet/...`) |
| `aksjonaer` | `skatteetaten:aksjonaer` | `/{rettighetspakke}/aksjonaerer/{organisasjonsnummer}` |
| `naeringsspesifikasjon` | `skatteetaten:naeringsspesifikasjon` | `/{rettighetspakke}/{inntektsaar}/{ident}` |
| `mvamelding` | `skatteetaten:mvamelding` | `/{rettighetspakke}/meldinger/{referanse}` |
| `mvafastsetting` | `skatteetaten:mvafastsetting` | **enno ikkje tildelt** — kommentert ut i properties |

`{rettighetspakke}` er ein **sti**-parameter (t.d. `frivillighetstotte`), ikkje
det same som scope. Verdiane fins i Skatteetaten sin rettighetspakke-innsyn.

## Test-endepunkt

| Kall | Meining |
|---|---|
| `GET /maskinporten/token` | henter token for konfigurert scope og returnerer dekode JWT-claims (iss/aud/scope/exp) |
| `GET /skattedata/apis` | listar konfigurerte API med scope, base-URL og stiltemplate |
| `GET /skattedata/{api}?<params>` |oyrer eitt kall mot API-et. Parameter som svarer til ein `{placeholder}` i stien blir sett inn i stien, resten blir query-parameter |

Døme:

```
curl "http://localhost:8080/skattedata/aksjonaer?rettighetspakke=frivillighetstotte&organisasjonsnummer=222222222&kalenderaar=2024"
```

Svar: `{"status":200,"body":"{...}","korrelasjonsid":"..."}` — eller
`{"status":"FEIL","feilmelding":"..."}` med rotarsaka (t.d. avvist
Maskinporten-token), slik at feil kan lesast utan å sla opp i loggen.

## Testdata

Testdata i Skatteetaten sitt testmiljo finst gjennom
[Tenor testdatasok](https://www.skatteetaten.no/skjema/testdata/).
Merk: for `mvamelding` finst det enno ikkje Tenor-sok — referansar ma hentast
frå Skatteetaten si hendelsesliste.

## Feilmeldingar

- `MP-100 invalid_grant / Invalid assertion` — feil client-id, nøkkel eller KEYID
- `MP-101`/`invalid_scope` — scopet er ikkje tildelt klienten var
- API-feilkoder (MFA-/MVA-/ABE-serien) kjem ut i `body` fra test-kallet,
  sjå kvart API si dokumentasjon pa
  [skatteetaten.github.io/api-dokumentasjon](https://skatteetaten.github.io/api-dokumentasjon/)

## Merknadar

- Privatnoklar og andre secrets skal **alltid** halde ut av git (sjá `.gitignore`).
- Miljovariablar kan overstyre: `MASKINPORTEN_CLIENT_ID`, `MASKINPORTEN_KEY_ID`,
  `MASKINPORTEN_PRIVATE_KEY_FILE`, `MASKINPORTEN_SCOPE`.
- API-skjema: `api.swaggerhub.com/apis/skatteetaten/<navn>/<versjon>`
  (versjon med punktum, t.d. `1.1.2`).
