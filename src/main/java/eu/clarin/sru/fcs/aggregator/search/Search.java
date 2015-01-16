package eu.clarin.sru.fcs.aggregator.search;

import eu.clarin.sru.client.SRUVersion;
import java.util.List;
import eu.clarin.sru.client.SRUClientException;
import eu.clarin.sru.client.SRUSearchRetrieveRequest;
import eu.clarin.sru.client.SRUSearchRetrieveResponse;
import eu.clarin.sru.client.fcs.ClarinFCSRecordData;
import eu.clarin.sru.fcs.aggregator.client.ThrottledClient;
import eu.clarin.sru.fcs.aggregator.scan.Corpus;
import eu.clarin.sru.fcs.aggregator.scan.Statistics;
import eu.clarin.sru.fcs.aggregator.util.SRUCQL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import opennlp.tools.tokenize.TokenizerModel;
import org.slf4j.LoggerFactory;

/**
 * Class representing a search operation
 *
 * @author Yana Panchenko
 * @author edima
 */
public class Search {

	private static final org.slf4j.Logger log = LoggerFactory.getLogger(Search.class);

	private static final String SEARCH_RESULTS_ENCODING = "UTF-8";

	private static final AtomicLong counter = new AtomicLong(Math.abs(new Random().nextInt()));

	private final Long id;
	private final String searchLanguage;
	private final List<Request> requests = Collections.synchronizedList(new ArrayList<Request>());
	private final List<Result> results = Collections.synchronizedList(new ArrayList<Result>());
	private final Statistics statistics;

	public Search(ThrottledClient searchClient, SRUVersion version,
			Statistics statistics, List<Corpus> corpora, String searchString,
			String searchLanguage, int startRecord, int maxRecords
	) {
		this.id = counter.getAndIncrement();
		this.searchLanguage = searchLanguage;
		this.statistics = statistics;
		for (Corpus corpus : corpora) {
			executeSearch(searchClient, version, corpus, searchString, startRecord, maxRecords);
		}
	}

	private Request executeSearch(ThrottledClient searchClient, SRUVersion version, final Corpus corpus, String searchString, int startRecord, int maxRecords) {
		final Request request = new Request(corpus, searchString, startRecord, startRecord + maxRecords - 1);
		log.info("Executing search in '{}' query='{}' maxRecords='{}'", corpus, searchString, maxRecords);

		SRUSearchRetrieveRequest searchRequest = new SRUSearchRetrieveRequest(corpus.getEndpointUrl());
		searchRequest.setVersion(version);
		searchRequest.setMaximumRecords(maxRecords);
		searchRequest.setRecordSchema(ClarinFCSRecordData.RECORD_SCHEMA);
		searchRequest.setQuery("\"" + searchString + "\"");
		searchRequest.setStartRecord(startRecord);
		if (request.hasCorpusHandle()) {
			searchRequest.setExtraRequestData(SRUCQL.SEARCH_CORPUS_HANDLE_PARAMETER, corpus.getHandle());
		}
		requests.add(request);

		try {
			searchClient.searchRetrieve(searchRequest, new ThrottledClient.SearchCallback() {
				@Override
				public void onSuccess(SRUSearchRetrieveResponse response, ThrottledClient.Stats stats) {
					statistics.addEndpointDatapoint(corpus.getInstitution(), corpus.getEndpointUrl(), stats.getQueueTime(), stats.getExecutionTime());
					results.add(new Result(request, response, null));
					requests.remove(request);
				}

				@Override
				public void onError(SRUSearchRetrieveRequest srureq, SRUClientException xc, ThrottledClient.Stats stats) {
					statistics.addEndpointDatapoint(corpus.getInstitution(), corpus.getEndpointUrl(), stats.getQueueTime(), stats.getExecutionTime());
					statistics.addErrorDatapoint(corpus.getInstitution(), corpus.getEndpointUrl(), xc);
					results.add(new Result(request, null, xc));
					requests.remove(request);
				}
			});
		} catch (Throwable xc) {
			log.error("SearchRetrieve error for " + corpus.getEndpointUrl(), xc);
		}
		return request;
	}

	public Long getId() {
		return id;
	}

	public List<Request> getRequests() {
		List<Request> copy = new ArrayList<>();
		synchronized (requests) {
			copy.addAll(requests);
		}
		return copy;
	}

	public List<Result> getResults() {
		List<Result> copy = new ArrayList<>();
		synchronized (results) {
			copy.addAll(results);
		}
		return copy;
	}

	public Statistics getStatistics() {
		return statistics;
	}

	public void exportTCF(TokenizerModel tokenizerModel) throws ExportException {
		byte[] bytes = Exports.getExportTokenizedTCF(results, searchLanguage, tokenizerModel);
		if (bytes != null) {
			Filedownload.save(bytes, "text/tcf+xml", "ClarinDFederatedContentSearch.xml");
		}
	}

	public void exportText() {
		String text = Exports.getExportText(results);
		if (text != null) {
			Filedownload.save(text, "text/plain", "ClarinDFederatedContentSearch.txt");
		}
	}

	void exportExcel() throws ExportException {
		byte[] bytes = Exports.getExportExcel(results);
		if (bytes != null) {
			Filedownload.save(bytes, "text/tcf+xml", "ClarinDFederatedContentSearch.xls");
		}
	}

	void exportPWText(String user, String pass) {
		byte[] bytes = null;
		try {
			String text = Exports.getExportText(results);
			if (text != null) {
				bytes = text.getBytes(SEARCH_RESULTS_ENCODING);
			}
		} catch (Exception ex) {
			Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
		}
		if (bytes != null) {
			DataTransfer.uploadToPW(user, pass, bytes, "text/plan", ".txt");
		}
	}

	String useWebLichtOnText() {
		String url = null;
		try {
			String text = Exports.getExportText(results);
			if (text != null) {
				byte[] bytes = text.getBytes(SEARCH_RESULTS_ENCODING);
				url = DataTransfer.uploadToDropOff(bytes, "text/plan", ".txt");
			}
		} catch (Exception ex) {
			Logger.getLogger(Search.class.getName()).log(Level.SEVERE, null, ex);
		}
		return url;
	}

	String useWebLichtOnToks(TokenizerModel tokenizerModel) throws ExportException {
		String url = null;
		byte[] bytes = Exports.getExportTokenizedTCF(results, searchLanguage, tokenizerModel);
		if (bytes != null) {
			url = DataTransfer.uploadToDropOff(bytes, "text/tcf+xml", ".tcf");
		}
		return url;
	}

	void exportPWExcel(String user, String pass) throws ExportException {
		byte[] bytes = Exports.getExportExcel(results);
		if (bytes != null) {
			DataTransfer.uploadToPW(user, pass, bytes, "application/vnd.ms-excel", ".xls");
		}
	}

	public void exportPWTCF(String user, String pass, TokenizerModel tokenizerModel) throws ExportException {
		byte[] bytes = Exports.getExportTokenizedTCF(results, searchLanguage, tokenizerModel);
		if (bytes != null) {
			DataTransfer.uploadToPW(user, pass, bytes, "text/tcf+xml", ".tcf");
		}
	}

	public void exportCSV() {
		String csv = Exports.getExportCSV(results, ";");
		if (csv != null) {
			Filedownload.save(csv, "text/plain", "ClarinDFederatedContentSearch.csv");
		}
	}

	public void exportPWCSV(String user, String pass) {
		String csv = Exports.getExportCSV(results, ";");
		if (csv != null) {
			DataTransfer.uploadToPW(user, pass, csv.getBytes(), "text/csv", ".csv");
		}
	}

	public void shutdown() {
		// nothing to do 
	}
}
