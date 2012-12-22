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

package com.github.rnewson.couchdb.lucene;

import org.json.JSONObject;
import org.junit.Test;

public class JsonTest {

    @Test
    public void testEscapedChars() throws Exception {
        final String str = "{\"seq\":1503,\"id\":\"11dca825e8b19e40bd675345e05afa24\",\"changes\":[{\"rev\":\"2-bb1fba3e33ed2e8b78412fe27c8c6474\"}],\"doc\":{\"_id\":\"11dca825e8b19e40bd675345e05afa24\",\"_rev\":\"2-bb1fba3e33ed2e8b78412fe27c8c6474\",\"query_params\":{\"{\\\"action\\\":\\\"answer\\\",\\\"session-id\\\":41,\\\"answer\\\":5}\":\"\"},\"stack_trace\":\"  File \\\"/usr/local/lib/python2.6/dist-packages/Django-1.2.1-py2.6.egg/django/core/handlers/base.py\\\", line 95, in get_response\\n    response = middleware_method(request, callback, callback_args, callback_kwargs)\\n  File \\\"/var/src/bhoma/bhoma/middleware.py\\\", line 37, in process_view\\n    return login_required(view_func)(request, *view_args, **view_kwargs)\\n  File \\\"/usr/local/lib/python2.6/dist-packages/Django-1.2.1-py2.6.egg/django/contrib/auth/decorators.py\\\", line 25, in _wrapped_view\\n    return view_func(request, *args, **kwargs)\\n  File \\\"/var/src/bhoma/bhoma/apps/xforms/views.py\\\", line 74, in player_proxy\\n    response, errors = post_data(data, settings.XFORMS_PLAYER_URL, content_type=\\\"text/json\\\")\\n  File \\\"/var/src/bhoma/bhoma/utils/post.py\\\", line 34, in post_data\\n\",\"doc_type\":\"ExceptionRecord\",\"url\":\"http://10.10.10.10/xforms/player_proxy\",\"clinic_id\":\"5010110\",\"date\":\"2010-09-08T14:39:11Z\",\"message\":\"[Errno 24] Too many open files: '/tmp/tmp8xIQb7'\",\"type\":\"<type 'exceptions.IOError'>\"}}";
        new JSONObject(str);
    }

}
