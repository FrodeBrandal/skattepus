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
| Public key registrert i Maskinporten-portalen | gir oss ein **KEYID** (UUID — ikkje fingeravtrykket) |
| Client-id | Maskinporten-portalen (var test-klient) |
| Tildelte scope | Skatteetaten tildeler per verksemd — sjå `rettighetspakke-innsyn.skatteetaten.no` |

Noverande status: client-id, privatnøkkel og KEYID er på plass, og **alle fire
scope er tildelte klienten** (aksjebeholdning, aksjonaer, naeringsspesifikasjon,
mvafastsetting). Test-scope utan `/test`-suffiks er det som
gjeld (`/test`-varianten blir avvist som ugyldig).

## Oppsett lokalt

1. Plasser privatnøkkelen som `src/main/resources/keys/maskinporten-test-private.pem`
   (eller peik ein annan filsti via `skattepus.maskinporten.private-key-file`).
2. Lag `src/main/resources/keys/secret.yml` (allereie git-ignorert) med:
    ```yaml
    skattepus:
      maskinporten:
        client-id: <client-id fra Maskinporten-portalen>
        key-id: <KEYID fra portalen>
        scope: skatteetaten:aksjebeholdning   # brukast berre av /maskinporten/token
        rights-packages: <pakke1>,<pakke2>    # rettighetspakkene alle API-testane køyrer mot
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
| `mvafastsetting` | `skatteetaten:mvafastsetting` | `/{rettighetspakke}/fastsettinger/{organisasjonsnummer}` + query `fraOgMed`/`tilOgMed` |

`{rettighetspakke}` er ein **sti**-parameter, ikkje det same som scope. Lista
over pakker som skal testast (for Lottstift: `lottstiftFrivillighetsstoette` og
`lottstiftStiftelsestilsyn`) ligg som hemmelegheit i `keys/secret.yml` under
`skattepus.maskinporten.rights-packages` — **alle API-testane køyrer éin
dynamisk test per pakke** (`@TestFactory` + `forEveryRightsPackage`).

## Testar — berre reelle kall

Det finst eitt einaste testlag, og det blir **ikkje mocka noko** — korkje HTTP eller
Maskinporten. Kvar testklasse dekkjer eitt API og arvar `AbstractSkatteetatenTest`
(`callApi` + testdata + feiltydning):

| Testklasse | API |
|---|---|
| `MaskinportenTokenTest` | token for configurert scope + alle API-scope |
| `AksjebeholdningTest` | `aksjebeholdning` |
| `AksjonaerIVerksemdTest` | `aksjonaer` |
| `NaeringsspesifikasjonTest` | `naeringsspesifikasjon` |
| `MvaFastsettingTest` | `mvafastsetting` |

Alle køyrer ekte privatnøkkel, ekte client-id/KEYID frå `keys/secret.yml` og ekte
HTTP mot `*.skatteetaten-test.no`. Dei fire API-testane er `@TestFactory` som
opnar **éin dynamisk test per rettighetspakke** frå `secret.yml` — altså 4 API ×
2 pakker = 8 reelle API-kall.

Derfor krev `./mvnw test` hemmelegheiter på disk og nettverk. Ein CI-pipeline utan
nøklar vil difor feile, og det er meint: ein grønn test skal vere eit reellt
oppkoblingsbevis, ikkje eit mock-bevis.

Testdata (fnr/orgnr/år) ligg i **`src/main/resources/keys/live-testdata.properties`**
— ei git-ignorert fil ved sidan av `secret.yml`, altså **ingen miljøvariellar**:

```properties
year=2025                                   # merk årsgatelynda, sjå under
person.ident=06854699537                    # fnr frå Test-fanen (ventar datasett-autorisasjon, sjå status)
orgnr=313136841                             # orgnr med aksjonær-data
naering.ident=14895398862                   # ident med næringsspesifikasjon (valfri — fell tilbake på orgnr)
mva.orgnr=312409852                         # orgnr med mva-fastsetting (valfri — fell tilbake på orgnr)
mva.year=2021
```

Kvar API har sine eigne datasett: `naering.ident`/`mva.orgnr` (og `<api>.year`)
overstyrer dei felles nyklane når dei er fylte ut. Rettighetspakkene ligg
ikkje her — dei er hemmelege og ligg i `secret.yml`.

Køyr:

```
./mvnw clean test
```

Bruk `clean` første gong etter endringar i test-ressursar: ein gammel
`target/test-classes/application.properties` ligg att etter sletta filer og
**skyggjer** `application.properties` i main — då blir hemmelegdene frå
`secret.yml` borte og testane feilar med `test-client-id`.

Testen skriv status + rå body for kvart API til stdout, deserialiserer svaret
til typa recordar og skriv ei `[LIVE] MOTTOK`-kvittering med dei faktiske tala
vi tek imot (tal aksjonærer, sum avgift, driftskostnad/osb.), og prøver token
for kvart konfigurert scope. **Ingen testar blir hoppa over** — alt som ikkje
verkar, feilar med ei melding som forklarer årsaka og kva som må gjerast:

| Feil | Meldinga forklarar |
|---|---|
| `MP-250`/`MP-200 invalid_scope` | scope-et er ikkje tildelt klienten — be Skatteetaten tildele det i test-miljøet |
| `MP-100 invalid_grant` | assertion avvist — feil client-id, KEYID eller privatnøkkel |
| `400 … rettighetspakke er ugyldig` | token er godteken, men `{rettighetspakke}` er gal — rett lista i `secret.yml` |
| `401` | scope dekkjer ikkje API-et / audience mot feil miljø |
| `404` | oppgitt fnr/orgnr har ikkje data — bytt testdata |
| `5xx` | feil hos Skatteetaten — bruk korrelasjonsid i melding til deira support |
| manglande testdata | kva nøkkel som manglar i fila, og kvar ein finn verdien |
| `ABE-006`/`AIV-006`/`NS-006` «…år er ugyldig» | **årsgatelynde**: testmiljøet serverer berre nyare år (2025 per okt 2026), sjolv om Test-fanen i dokumenta listar eldre |
| `ABE-005` (403) | året slepp gjennom, men datasettet er ikkje autorisert for klienten/rettighetspakken — be Skatteetaten om tildeling |

**Status på live-testane (okt 2026):** `aksjonaer` (orgnr 313136841, 2025),
`naeringsspesifikasjon` (ident 14895398862, 2025) og `mvafastsetting`
(orgnr 312409852, 2021) er **grøne med ekte data for begge rettighetspakkene**.
Einaste attståande blokkering: aksjebeholdning-datasettet gir `ABE-005` 403 for
året som slepp gjennom årsgatelynda (begge pakker) — klienten er ikkje autorisert
for fnr 06854699537, må spørjast Skatteetaten (bruk korrelasjonsid frå testen).

## Manuell probe (same endpoints som live-testen)

| Kall | Meining |
|---|---|
| `GET /maskinporten/token` | henter token for konfigurert scope og returnerer dekode JWT-claims (iss/aud/scope/exp) |
| `GET /skattedata/apis` | listar konfigurerte API med scope, base-URL og stiltemplate |
| `GET /skattedata/{api}?<params>` | gjer eitt kall mot API-et. Parameter som svarer til ein `{placeholder}` i stien blir sett inn i stien, resten blir query-parameter |

Døme:

```
curl "http://localhost:8080/skattedata/aksjonaer?rettighetspakke=lottstiftFrivillighetsstoette&organisasjonsnummer=313136841&kalenderaar=2025"
```

Svar: `{"status":200,"body":"{...}","korrelasjonsid":"..."}` — eller
`{"status":"ERROR","errorMessage":"..."}` med rotarsaka (t.d. avvist
Maskinporten-token), slik at feil kan lesast utan å sla opp i loggen.

## Testdata

Desse API-a har **ikkje** Tenor-søk — gyldig testdata ligg i **Test-fanen** i
dokumentasjonen for kvart API, t.d.
[aksjebeholdning](https://skatteetaten.github.io/api-dokumentasjon/api/aksjebeholdning?tab=Test)
og [mvafastsetting](https://skatteetaten.github.io/api-dokumentasjon/api/mvafastsetting?tab=Test).
Merk at tabellen kan vere utdatert: årsgatelynde i testmiljøet serverer berre
nyare år enn det som står der.

## Feilmeldingar

- `MP-100 invalid_grant / Invalid assertion` — feil client-id, nøkkel eller KEYID
- `MP-200`/`MP-250` `invalid_scope` — scopet er ikkje tildelt klienten
- `MP-101` — ugyldig scope-format (bruk scope utan miljø-suffiks)
- API-feilkoder (MFA-/MVA-/ABE-serien) kjem ut i `body` fra test-kallet,
  sjå kvart API si dokumentasjon pa
  [skatteetaten.github.io/api-dokumentasjon](https://skatteetaten.github.io/api-dokumentasjon/)

## Merknadar

- Privatnoklar og andre secrets skal **alltid** halde ut av git (sjá `.gitignore`).
- Miljovariablar kan overstyre: `MASKINPORTEN_CLIENT_ID`, `MASKINPORTEN_KEY_ID`,
  `MASKINPORTEN_PRIVATE_KEY_FILE`, `MASKINPORTEN_SCOPE`.
- API-skjema: `api.swaggerhub.com/apis/skatteetaten/<navn>/<versjon>`
  (versjon med punktum, t.d. `1.1.2`).
