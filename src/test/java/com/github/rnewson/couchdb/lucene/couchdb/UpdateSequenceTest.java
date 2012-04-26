package com.github.rnewson.couchdb.lucene.couchdb;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class UpdateSequenceTest {

	@Test
	public void couchdbSequence() {
		assertThat(UpdateSequence.parseUpdateSequence("1234"), notNullValue());
	}

	@Test
	public void bigcouch3Sequence() {
		assertThat(
				UpdateSequence
						.parseUpdateSequence("79521-g1AAAAGbeJzLYWBg4MhgTmEQT8pMT84vTc5wMDQ30jM00zO0BG"
								+ "JjgxygAqZEhiT5____ZyUxMKi1EVSdpAAkk-yhGtRdCWtwAGmIh9lwi7CGBJCGepgN0gQ"
								+ "15LEASYYGIAXUMx-syYlITQsgmvaDneZDpKYDEE33wZpOE6npAUQTJBA6sgABPG9K"),
				notNullValue());
	}

	@Test
	public void bigcouch4Sequence() {
		assertThat(
				UpdateSequence
						.parseUpdateSequence("[79521,\"g1AAAAGbeJzLYWBg4MhgTmEQT8pMT84vTc5wMDQ30jM00zO0BG"
								+ "JjgxygAqZEhiT5____ZyUxMKi1EVSdpAAkk-yhGtRdCWtwAGmIh9lwi7CGBJCGepgN0gQ"
								+ "15LEASYYGIAXUMx-syYlITQsgmvaDneZDpKYDEE33wZpOE6npAUQTJBA6sgABPG9K\"]"),
				notNullValue());
	}
}
