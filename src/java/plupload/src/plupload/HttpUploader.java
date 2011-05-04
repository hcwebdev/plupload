package plupload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;


public class HttpUploader {

  private int retries;
  private String cookie;
  private URI target_uri;
  private String target_file_name;
  private String file_name;
  private String file_data_name;
  private Map<String, String> params;
  private HttpClient httpclient;

  public HttpUploader(URI uri, String name, String file_name, String file_data_name, int chunk_retries, String cookie, Map<String, String> params){
    this.httpclient = HttpUtil.getHttpClient();
    this.retries = chunk_retries;
    this.cookie = cookie;
    this.target_uri = uri;
    this.file_name = name;
    this.target_file_name = file_name;
    this.file_data_name = file_data_name;
    this.params = params;
  }

  public int sendChunk(byte[] data, int len, int chunk, long chunks) throws NoSuchAlgorithmException, URISyntaxException,
      ClientProtocolException, IOException {
    HttpPost httppost = new HttpPost(target_uri);
    ByteArrayBody data_bab = new ByteArrayBody(data, file_name);
    MultipartEntity entity = new MultipartEntity(
      HttpMultipartMode.BROWSER_COMPATIBLE);
    entity.addPart(file_data_name, data_bab);
    entity.addPart("name", new StringBody(target_file_name));
    entity.addPart("chunk", new StringBody(Integer.toString(chunk)));
    entity.addPart("chunks", new StringBody(Long.toString(chunks)));
    for (Map.Entry<String,String> param : params.entrySet()) {
      entity.addPart(param.getKey(), new StringBody(param.getValue()));
    }
    httppost.setEntity(entity);
    httppost.addHeader("Cookie", cookie);
    
    HttpResponse response = httpclient.execute(httppost);

    HttpEntity resEntity = response.getEntity();
    int status_code = response.getStatusLine().getStatusCode();
    String body = EntityUtils.toString(resEntity);

    if (status_code != 200) {
      if (retries > 0) {
        retries--;
        sendChunk(data, len, chunk, chunks);
      } else {
        throw new IOException("Exception uploading to server: " + body);
      }
    }
    return status_code;
  }
}
