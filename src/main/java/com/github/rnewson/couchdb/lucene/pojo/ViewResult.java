package com.github.rnewson.couchdb.lucene.pojo;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class ViewResult {

	private long offset;
	private List<ViewRow> rows;
	private long totalRows;

	public long getOffset() {
		return offset;
	}

	public ViewRow getRow(final int index) {
		return rows.get(index);
	}

	public List<ViewRow> getRows() {
		return rows;
	}

	public long getTotalRows() {
		return totalRows;
	}

	@JsonProperty("offset")
	public void setOffset(final long offset) {
		this.offset = offset;
	}

	@JsonProperty("rows")
	public void setRows(final List<ViewRow> rows) {
		this.rows = rows;
	}

	@JsonProperty("total_rows")
	public void setTotalRows(final long totalRows) {
		this.totalRows = totalRows;
	}

}
