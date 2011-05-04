package plupload;

import java.io.File;
import java.io.IOException;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import netscape.javascript.JSObject;

public class Plupload extends Applet2 {
  
  private PluploadFile current_file;
  private JFileChooser dialog;
  private boolean dialog_open = false;

  private String uploader_id;
  private String base_url;
  private Map<String, PluploadFile> files;
  private Map<String, String> params;
  private int id_counter = 0;

  @Override
  public void init() {
    super.init();
    
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    base_url = getCodeBase().toString();
    files = new HashMap<String, PluploadFile>();
    uploader_id = getParameter("id");
    params = new HashMap<String, String>();

    try {
      dialog = new JFileChooser();
      dialog.setMultiSelectionEnabled(true);
    } catch (AccessControlException e) {
      JSObject
          .getWindow(this)
          .eval(
              "alert('Please approve the digital signature of the applet. Close the browser and start over')");
    }
    publishEvent(Event.INIT);
  }
  
  // LiveConnect calls from JS
  @SuppressWarnings("unchecked")
  public void setFileFilters(final String filters, final int multi) {
    System.out.println("setting filters: " + filters);
    try {
      AccessController.doPrivileged(new PrivilegedExceptionAction() {
        public Object run() throws IOException, Exception {
          if (multi > 0) {
            dialog.setMultiSelectionEnabled(true);
          } else {
            dialog.setMultiSelectionEnabled(false);
          }
          if (!filters.equals("")) {
            dialog
              .setFileFilter(new javax.swing.filechooser.FileFilter() {

                @Override
                public String getDescription() {
                  // TODO Auto-generated method stub
                  return null;
                }

                @Override
                public boolean accept(File f) {
                  if (f.isDirectory()) {
                    return true;
                  }
                  for (String filter : filters.split(",")) {
                    if (f.getName().toLowerCase().endsWith(
                        filter.toLowerCase())) {
                      return true;
                    }
                  }
                  return false;
                }
              });
          }
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      Exception ex = e.getException();
      if (ex instanceof IOException) {
        publishIOError(ex);
      } else if (ex instanceof Exception) {
        publishError(ex);
      }
    }
  }
  
  @SuppressWarnings("unchecked")
  public void uploadFile(final String id, final String url,
      final String file_name, final String file_data_name,
      final String cookie, final int chunk_size, final int retries) {
    final PluploadFile file = files.get(id);
    final Map<String, String> params = this.params;
    final String base_url = this.base_url;
    if (file != null) {
      this.current_file = file;
    }
    try {
      // Because of LiveConnect our security privileges are degraded
      // elevate them again.
      AccessController.doPrivileged(new PrivilegedExceptionAction() {
        public Object run() throws IOException, Exception {
          file.upload(url, file_name, file_data_name, chunk_size, retries, cookie, base_url, params);
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      Exception ex = e.getException();
      if (ex instanceof IOException) {
        publishIOError(ex);
      } else {
        publishError(ex);
      }
    }
  }
  
  public void initUpload() {
    this.params.clear();
  }
  
  public void setParam(final String attr, final String value) {
    this.params.put(attr, value);
  }
  
  public void removeFile(String id) {
    files.remove(id);
  }

  public void clearFiles() {
    files.clear();
  }

  @SuppressWarnings("unchecked")
  public void openFileDialog() {
    // System.out.println("opening dialog");
    if (dialog_open) {
      // FIXME: bring openDialog to front
      return;
    }
    dialog_open = true;
    AccessController.doPrivileged(new PrivilegedAction() {
      public Object run() {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            int file_chose_return_value = dialog
                .showOpenDialog(Plupload.this);
            // System.out.println("openDialog finished");
            // blocks until file selected
            if (file_chose_return_value == JFileChooser.APPROVE_OPTION) {
              if (dialog.isMultiSelectionEnabled()) {
                // System.out.println("used multi file selection dialog");
                for (File f : dialog.getSelectedFiles()) {
                  // Wiredness: If PluploadFile extends Thread
                  // it just stopped here in my production
                  // environment
                  PluploadFile file = new PluploadFile(
                      id_counter++, f);
                  selectEvent(file);
                }
              } else {
                // System.out.println("used single file selection dialog");
                File f = dialog.getSelectedFile();
                PluploadFile file = new PluploadFile(
                  id_counter++, f);
                selectEvent(file);
              }
            }
            dialog_open = false;
          }
        });
        return null;
      }
    });
  }

  // Events
  private enum Event {
    CLICK("Click"), 
    INIT("Init"),
    SELECT_FILE("SelectFiles"), 
    UPLOAD_PROCESS("UploadProcess"), 
    UPLOAD_CHUNK_COMPLETE("UploadChunkComplete"), 
    SKIP_UPLOAD_CHUNK_COMPLETE("SkipUploadChunkComplete"), 
    IO_ERROR("IOError"), 
    GENERIC_ERROR("GenericError");

    private String name;

    Event(String name) {
      this.name = name;
    }

    private String getName() {
      return name;
    }
  }

  private void publishEvent(Event e, Object ... args) {
    publishEvent(uploader_id, e.getName(), args);
  }

  private void publishIOError(Exception e) {
    publishEvent(Event.IO_ERROR, new PluploadError(e.getMessage(), this.current_file.id));
  }

  private void publishError(Exception e) {
    publishEvent(Event.GENERIC_ERROR, new PluploadError(e.getMessage(), this.current_file.id));
  }

  private void selectEvent(PluploadFile file) {
    System.out.println("selectEvent");
    // handles file add from file chooser
    files.put(file.id + "", file);

    file.addFileUploadListener(new FileUploadListener() {

      @Override
      public void uploadProcess(PluploadFile file) {
        publishEvent(Event.UPLOAD_PROCESS, file);
      }

      @Override
      public void ioError(IOException e) {
        publishIOError(e);

      }

      @Override
      public void genericError(Exception e) {
        publishError(e);
      }
    });

    publishEvent(Event.SELECT_FILE, file.toString());
  }
}
