package plupload;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.utils.URIUtils;

public class PluploadFile /*extends Thread*/{

  public int id;
  
  private int chunk;      // current chunk number
  private int chunk_size;
  private long chunks;    // chunks uploaded at client
  private long loaded;    // bytes uploaded
  
  private String name;
  private long size;  
  
  private File file;
  private byte[] buffer;
  private List<FileUploadListener> file_upload_listeners = new ArrayList<FileUploadListener>();
  private InputStream stream;
  private HttpUploader uploader;

  public PluploadFile(int id, File file) {
    this.id = id;
    this.name = file.getName().replace("\"", "\\\"").replace("'", "\\'");
    this.size = file.length();
    this.file = file;
  }

  protected void prepare(String upload_uri, String file_name, String file_data_name, int chunk_size, int retries, String cookie, String base_url, Map<String, String> params)
      throws URISyntaxException, IOException {
    if(size == 0){
      throw new IOException("File is empty!");
    }
    URI uri = fix_uri(base_url, upload_uri);
    uploader = new HttpUploader(uri, name, file_name, file_data_name, retries, cookie, params);
    this.chunk_size = chunk_size;
    stream = new BufferedInputStream(new FileInputStream(file), chunk_size);
    chunks = (size + chunk_size - 1) / chunk_size;
    buffer = new byte[chunk_size];
    chunk = 0;
    loaded = 0;
  }
  
  public void upload(String upload_uri, String file_name, String file_data_name, int chunk_size, int retries, String cookie, String base_url, Map<String, String> params)
      throws IOException, NoSuchAlgorithmException, URISyntaxException, ParseException {
    prepare(upload_uri, file_name, file_data_name, chunk_size, retries, cookie, base_url, params);
    
    Thread uploadThread = new Thread(){
      
      @Override
      public void run(){
          try{
            uploadChunks();
          }
          catch(IOException e){
            e.printStackTrace();
            ioErrorAction(e);
          }
          catch(Exception e){
            e.printStackTrace();
            genericErrorAction(e);
          }
        }
    };
    uploadThread.start();
  }

  public void uploadChunks() throws NoSuchAlgorithmException,
      ClientProtocolException, URISyntaxException, IOException {
    while(chunk != chunks){
      int bytes_read = stream.read(buffer);

      uploader.sendChunk(buffer, bytes_read, chunk, chunks);

      loaded += bytes_read;
      chunk++;

      uploadProcessAction();
    }
  }
  
  public URI fix_uri(String base_url, String uri) throws URISyntaxException {
    String lc_uri = uri.toLowerCase();
    if (lc_uri.startsWith("http://") || lc_uri.startsWith("https://")) {
      return new URI(uri);
    } else if (uri.startsWith("/")) {
      URI base_uri = new URI(base_url);
      return URIUtils.createURI(base_uri.getScheme(), base_uri.getHost(),  base_uri.getPort(), uri, base_uri.getQuery(), null);
    } else {
      return new URI(base_url + uri);
    }
  }

  public void addFileUploadListener(FileUploadListener listener) {
    file_upload_listeners.add(listener);
  }

  private void uploadProcessAction() {
    for (FileUploadListener f : file_upload_listeners) {
      f.uploadProcess(this);
    }
  }
  
  private void ioErrorAction(IOException e){
    for(FileUploadListener f : file_upload_listeners){
      f.ioError(e);
    }
  }
  
  private void genericErrorAction(Exception e) {
    for(FileUploadListener f : file_upload_listeners){
      f.genericError(e);
    }
  }
  
  public String toString(){
    return "{\"chunk\":" + chunk + 
    ",\"chunks\":" + chunks + 
    ",\"name\":\"" + name + "\"" +
    ",\"loaded\":" + loaded + 
    ",\"size\":" + size + 
    ",\"id\":" + id +
    "}";
  }

}
