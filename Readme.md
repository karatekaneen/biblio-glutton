# biblio-glutton

Framework dedicated to bibliographic information. It includes:

- a fast and accurate bibliographical reference matching service, supporting as input raw bibliographical references or combination of key metadata, 
- a fast metadata look-up service,
- various mapping between DOI, PMID, PMC, ISTEX ID and ark, integrated in the bibliographical service,
- Open Access resolver: Integration of Open Access links via the Unpaywall dataset from Impactstory,
- MeSH classes mapping for PubMed articles.

The framework is designed both for speed and matching accuracy. 

## Bibliographical data look-up and matching

For building the service, you will need these resources:

* CrossRef metadata dump: available via the Crossref Metadata Plus subscription or at Internet Archive, see https://github.com/greenelab/crossref,

* DOI to PMID and PMC mapping: available at [Europe PMC](ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/),

* Optionally, to get Open Access links, the Unpaywall dataset,

* Optionally, for getting ISTEX identifier informations, you need to build the ISTEX ID mapping, see bellow. 

The bibliographical matching service uses a combination of high performance embedded databases (LMDB), for fast look-up and cache, and  Elasticsearch for text-based search. As Elasticsearch is much slower than embedded databases, it is used only when absolutely required. 

The databases and elasticsearch index must first be built from the resource files. The full service needs around 300GB of space for building these index and it is highly recommended to use SSD for best performance.

### Build the databases

Resource dumps will be compiled in high performance LMDB databases. The system can read compressed or plain text files files (gzip or .xz), so in practice you do not need to uncompress anything.

##### Build

> cd lookup

> ./gradlew clean build

All the following commands need to be launched under the subdirectory `lookup/`.

##### CrossRef metadata

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar crossref --input /path/to/crossref/json/file /path/to/your/configuration

Example (XZ files will be streamed directly from the compressed versions): 

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar crossref --input crossref-works.2018-09-05.json.xz data/config/config.yml
 
Note: by default the abstract and the bibliographical references included in CrossRef records are ignored to save some disk  space. TODO: add a parameter for controlling this in the config file.

##### PMID and PMC ID

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar pmid --input /path/to/pmid/csv/file /path/to/your/configuration 

Example: 

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar pmid --input PMID_PMCID_DOI.csv.gz data/config/config.yml 


##### OA via Unpaywall

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar unpaywall --input /path/to/unpaywall/json/file /path/to/your/configuration

Example: 

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar unpaywall --input unpaywall_snapshot_2018-06-21T164548_with_versions.jsonl.gz data/config/config.yml 

##### ISTEX

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar istex --input /path/to/istex/json/file /path/to/your/configuration

Example: 

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar istex --input istexIds.all.gz data/config/config.yml

Note: see bellow how to create this mapping file `istexIds.all.gz`. 

### Build the Elasticsearch index

A node.js utility under the subdirectory `matching/` is used to build the Elasticsearch index. It will take a couple of hours for the 100M crossref entries.

#### Install and configure

You need first to install and start ElasticSearch, latest version. Replace placeholder in the file `my_connection.js` to set the host name and port of the Elasticsearch server. 

Install the node.js module:

> cd matching/

> npm install


#### Build the index 

> node main -dump *PATH_TO_THE_CROSSREF_JSON_DUMP* index

Example:

```
> node main -dump ~/tmp/crossref-works.2018-09-05.json.xz index
```

Note than launching the above command will fully re-index the data, deleting existing index. The default name of the index is `crossref`, but this can be changed via the config file `matching/config.json`.

### The bibliographical look-up and matching REST API

Once the databases and index are built, the bibliographical REST API can be started. 

#### Start the server

> cd lookup/

> ./gradlew clean build

> java -jar build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar server data/config/config.yml

The last parameter is the path where your configuration file is located - the default path being here indicated. 

#### REST API

   - match record by DOI
        - `GET host:port/service/lookup?doi=DOI`
        - `GET host:port/service/lookup/doi/{DOI}`

    - match record by PMID
        - `GET host:port/service/lookup?pmid=PMID`
        - `GET host:port/service/lookup/pmid/{PMID}`

    - match record by PMC ID
        - `GET host:port/service/lookup?pmc=PMC`
        - `GET host:port/service/lookup/pmc/{PMC}`

    - match record by ISTEX ID
        - `GET host:port/service/lookup?istexid=ISTEXID`
        - `GET host:port/service/lookup/istexid/{ISTEXID}`

    - match record by article title and first author lastname
        - `GET host:port/service/lookup?atitle=ARTICLE_TITLE&firstAuthor=FIRST_AUTHOR_SURNAME[?postValidate=true]`
        
        The post validation optional parameter avoids returning records whose title and first author are too different from the searched ones.
    
    - match record by journal title or abbreviated title, volume and first page
        - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE`

    - match record by journal title or abbreviated title, volume, first page, and first author lastname
        - `GET host:port/service/lookup?jtitle=JOURNAL_TITLE&volume=VOLUME&firstPage=FIRST_PAGE&firstAuthor=FIRST_AUTHOR_SURNAME`

    - match record by raw citation string 
        - `GET host:port/service/lookup?biblio=BIBLIO_STRING`
        - `POST host:port/service/lookup/biblio` with `ContentType=text/plain` 

Open Access resolver API returns the OA PDF link (URL) by identifier: 

    - return best Open Access URL 
        - `GET host:port/service/oa?doi=DOI` return the best Open Accss PDF url for a given DOI 
        - `GET host:port/service/oa?pmid=PMID` return the best Open Accss PDF url for a given PMID 
        - `GET host:port/service/oa?pmc=PMC` return the best Open Accss PDF url for a given PMC ID

#### cURL examples

To illustrate the usage of the API, we provide some cURL example queries:

Bibliographical metadata lookup by DOI:

> curl http://localhost:8080/service/lookup?doi=10.1484/J.QUAESTIO.1.103624

Matching with title and first authort lastname:

> curl "http://localhost:8080/service/lookup?atitle=Naturalizing+Intentionality+between+Philosophy+and+Brain+Science.+A+Survey+of+Methodological+and+Metaphysical+Issues&firstAuthor=Pecere&postValidate=true"

> curl "http://localhost:8080/service/lookup?atitle=Naturalizing+Intentionality+between+Philosophy+and+Brain+Science&firstAuthor=Pecere&postValidate=false"

Matching with raw bibliographical reference string:

> curl "http://localhost:8080/service/lookup?biblio=Baltz,+R.,+Domon,+C.,+Pillay,+D.T.N.+and+Steinmetz,+A.+(1992)+Characterization+of+a+pollen-specific+cDNA+from+sunflower+encoding+a+zinc+finger+protein.+Plant+J.+2:+713-721"

Bibliographical metadata lookup by PMID:

> curl http://localhost:8080/service/lookup?pmid=1605817

Bibliographical metadata lookup by ISTEX ID:

> curl http://localhost:8080/service/lookup?istexid=E6CF7ECC9B002E3EA3EC590E7CC8DDBF38655723

Open Access resolver by DOI:

> curl "http://localhost:8080/service/oa?doi=10.1038/nature12373"


## ISTEX mapping

If you don't know what ISTEX is, you can safely skip this section.

### ISTEX identifier mapping

For creating a dump of all ISTEX identifiers associated with existing identifiers (DOI, ark, PII), use the node.js script as follow:

* install:

```bash
> cd scripts
>  npm install requestretry
```

* Generate the json dump:

```bash
> node dump-istexid-and-other-ids.js > istexIds.all
```

Be sure to have a good internet bandwidth for ensuring a high rate usage of the ISTEX REST API.

You can then move the json dump (e.g. `istexIds.all`) to the Istex data path indicated in the file `config/glutton.yaml` (by default `data/istex/`). 

### ISTEX to PubMed mapping

Be sure to have the PMID and PMCID mapping to DOI, available at Euro PMC saved under your pmcDirectory path indicated in `config/glutton.yaml` (by default `data/pmc`). As of September 2018, the mapping was still available [there](ftp://ftp.ebi.ac.uk/pub/databases/pmc/DOI/).

To create the ISTEX to PubMed mapping, run:

```bash
> java -Xmx1024m -jar target/com.scienceminer.glutton-0.0.1.one-jar.jar -exe istexPMID -addMeSH -out ../data/istex/istex2pmid.json
```

The resulting mapping will be written under the path introduced by the parameter `-out`.

The argument `-addMeSH` is optional, if present the mapping will include the MeSH classes.  

The mapping contains all the ISTEX identifier found in PubMed, associated with a PubMed ID, a PubMedCentral ID (if found), and - if the paramter `-addMeSH` is present, the MeSH classes corresponding to the article (descriptors with qualifiers, chemicals, and main topic information).


## License

Distributed under [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0). 