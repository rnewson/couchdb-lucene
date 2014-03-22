/*
 * Copyright Robert Newson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.rnewson.couchdb.lucene.couchdb;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

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
