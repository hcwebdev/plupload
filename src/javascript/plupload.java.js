/**
 * plupload.java.js
 *
 * Copyright 2011, Jacob Radford
 * Released under GPL License.
 *
 * License: http://www.plupload.com/license
 * Contributing: http://www.plupload.com/contributing
 */

// JSLint defined globals
/*global window:false, document:false, plupload:false, ActiveXObject:false, escape:false */

(function(window, document, plupload, undef) {
  var uploadInstances = {}, initialized = {};
  
  function getById(id) {
    return document.getElementById(id);
  };
  
  function paramify(value) {
    if (value === undef) {
      return "";
    } else if (value === true) {
      return "1";
    } else if (value === false) {
      return "0";
    }
    return value + "";
  };
  
  var javaplugin = function() {
    var versions = ['1.4.2', '1.5','1.6','1.7'],
        _version = "-1",
        version;
    
    function set_highest_version(potential_new_version){
      if(potential_new_version > _version){
        _version = potential_new_version;
      }
    }
    function getVersion(){
      if(navigator.userAgent.indexOf("MSIE") != -1){
        populateVersionsIE();
      }
      else{
        populateVersionsNotIE();
      }
      return _version;
    }
    function populateVersionsIE(){
      // 1.5 -> 1.5.0.0
      function pad(version){
        var max_dots = 3;
        var dots = version.replace(/[^.]/g, '').length;
        for(; dots < max_dots; dots++){
          version = version + ".0";
        }
        return version;
      }
      for(var i = 0; i < versions.length; i++){
        var version = pad(versions[i]);
        try{
          if(new ActiveXObject('JavaWebStart.isInstalled.' + version) != null){
            set_highest_version(version);
          }
        }
        catch(e){
        }
      }
    }
    function populateVersionsNotIE(){
       if(navigator.mimeTypes){
         for(var i = 0; i < versions.length; i++){
           var version = versions[i];
           if(navigator.mimeTypes['application/x-java-applet;version=' + version]){
             set_highest_version(version);
           }
         }
         // add the general one under ''
         if(navigator.mimeTypes['application/x-java-applet']){
           set_highest_version("");
         }
       }
    }
    return {
      version: function(){
        if (!version){
          version = getVersion();
        }
        return version;
      },
      present: function(){
        return (this.version() === "-1") ? false : true;
      }
    };
  }();
  
  plupload.applet = {
    /**
     * Will be executed by the Java runtime when it sends out events.
     *
     * @param {String} id => ID of the upload instance.
     * @param {String} name => Event name to trigger.
     * @param {Object} objstr => Parameters to be passed with event as string.
     */
    trigger : function(id, name, objstr) {
      // Detach the call so that error handling in the browser is presented correctly
      // FF / Safari mac breaks down if it's not detached here
      // can't do java -> js -> java
      setTimeout(function() {
          var uploader = uploadInstances[id], i, args;
          var file = objstr ? eval('(' + objstr + ')') : "";
          if (uploader) {
            uploader.trigger('applet:' + name, file);
          }
      }, 0);
    }
  };
  
  /**
   * JavaRuntime implementation. This runtime supports these features: multipart, chunks, progress.
   *
   * @static
   * @class plupload.runtimes.Applet
   * @extends plupload.Runtime
   */
  plupload.runtimes.Applet = plupload.addRuntime("java", {
    
    /**
     * Returns a list of supported features for the runtime.
     *
     * @return {Object} Name/value object with supported features.
     */
    getFeatures : function() {
      return {
        chunks: true,
        progress: true,
        multipart: true
      };
    },

    /**
     * Initializes the upload runtime. This method should add necessary items to the DOM and register events needed for operation. 
     *
     * @method init
     * @param {plupload.Uploader} uploader Uploader instance that needs to be initialized.
     * @param {function} callback Callback to execute when the runtime initializes or fails to initialize. If it succeeds an object with a parameter name success will be set to true.
     */
    init : function(uploader, callback) {
      var applet,
          appletContainer, 
          browseButton,
          waitCount = 0, 
          container = document.body;

      if(!javaplugin.present()){
        callback({success : false});
        return;
      }
      
      initialized[uploader.id] = false;
      uploadInstances[uploader.id] = uploader;
      
      appletContainer = document.createElement('div');
      appletContainer.id = uploader.id + '_applet_container';
      
      plupload.extend(appletContainer.style, {
        // move the 1x1 pixel out of the way. 
        position : 'absolute',
        left: '-9999px',
        zIndex : -1
      });
      
      appletContainer.className = 'plupload applet';
      container.appendChild(appletContainer);
      
      // Insert the Java applet inide the applet container
      appletContainer.innerHTML = '<object id="' + uploader.id + '_applet" type="application/x-java-applet" width="1" height="1">' +
        '<param name="mayscript" value="true" />' +
        '<param name="archive" value="' + uploader.settings.java_applet_url + '" />' +
        '<param name="id" value="' + escape(uploader.id) + '" />' +
        '<param name="code" value="plupload.Plupload" />' +
        '<param name="callback" value="plupload.applet.trigger" />' +
        '</object>';
      
      // var applet_html = '<object id="' + uploader.id + '_applet" type="application/x-java-applet" width="1" height="1">' +
      //   '<param name="mayscript" value="true" />' +
      //   '<param name="archive" value="' + uploader.settings.java_applet_url + '" />' +
      //   '<param name="id" value="' + escape(uploader.id) + '" />' +
      //   '<param name="code" value="plupload.Plupload" />' +
      //   '<param name="callback" value="plupload.applet.trigger" />' +
      //   '</object>';
      // setTimeout(function(){
      //   getById(uploader.id + '_applet_container').innerHTML = applet_html;
      // }, 0);
      
      function getAppletObj() {
        return document.getElementById(uploader.id + '_applet');
      }

      function waitLoad() {
        // Wait for 5 sec
        if (waitCount++ > 5000) {
          callback({success : false});
          return;
        }
        if (!initialized[uploader.id]) {
          setTimeout(waitLoad, 1);
        }
      }

      waitLoad();

      // Fix IE memory leaks
      appletContainer = null;

      // Wait for Java to send init event
      uploader.bind("Applet:Init", function() {  
        var lookup = {}, i, filterlist=[],
            multi_selection = (uploader.settings.multi_selection)?"1":"0";

        if(uploader.settings.filters){
          for(var i = 0, len = uploader.settings.filters.length; i < len; i++){
            filterlist.push(uploader.settings.filters[i].extensions);
          }
        }
        getAppletObj().setFileFilters(filterlist.join(","), multi_selection);

        // Prevent eventual reinitialization of the instance
        if (initialized[uploader.id]) {
          return;
        }
        initialized[uploader.id] = true;
        
        uploader.bind("Init", function() {
          plupload.addEvent(getById(uploader.settings.browse_button), 'click', function(e) {
            uploader.trigger('SelectFiles');
            e.preventDefault();
          }, uploader.id);
        });
        
        uploader.bind("UploadFile", function(up, file) {
            var settings = up.settings,
                multipart_params = up.settings.multipart_params || {};
            
            // converted to string since number type conversion is buggy in MRJ runtime
            // In Firefox Mac (MRJ) runtime every number is a double
            getAppletObj().initUpload();
            for(var k in uploader.settings.multipart_params){
              getAppletObj().setParam(k+"", paramify(args[k]));
            }
            getAppletObj().uploadFile(lookup[file.id] + "", 
                                      settings.url, 
                                      (file.target_name || file.name), 
                                      settings.file_data_name, 
                                      document.cookie, 
                                      settings.chunk_size + "", 
                                      (settings.retries || 3) + "");
        });

        uploader.bind("SelectFiles", function(up){
          getAppletObj().openFileDialog();
        });

        uploader.bind("Applet:UploadProcess", function(up, javaFile) {
          var file = up.getFile(lookup[javaFile.id]),
              finished = javaFile.chunk === javaFile.chunks;

          if (file.status != plupload.FAILED) {
            file.loaded = javaFile.loaded;
            file.size = javaFile.size;
            up.trigger('UploadProgress', file);
          }
          else{
            alert("uploadProcess status failed");
          }

          if (finished) {
            file.status = plupload.DONE;
            up.trigger('FileUploaded', file, {
              response : "File uploaded"
            });
          }
        });

        uploader.bind("Applet:SelectFiles", function(up, file) {
          var i, files = [], id;
          id = plupload.guid();
          lookup[id] = file.id;
          lookup[file.id] = id;

          files.push(new plupload.File(id, file.name, file.size));

          // Trigger FilesAdded event if we added any
          if (files.length) {
            uploader.trigger("FilesAdded", files);
          }
        });

        uploader.bind("Applet:GenericError", function(up, err) {
          uploader.trigger('Error', {
            code : plupload.GENERIC_ERROR,
            message : 'Generic error.',
            details : err.message,
            file : uploader.getFile(lookup[err.id])
          });
        });

        uploader.bind("Applet:IOError", function(up, err) {
          uploader.trigger('Error', {
            code : plupload.IO_ERROR,
            message : 'IO error.',
            details : err.message,
            file : uploader.getFile(lookup[err.id])
          });
        });

        uploader.bind("FilesRemoved", function(up, files) {
          for (var i = 0, len = files.length; i < len; i++) {
            getAppletObj().removeFile(lookup[files[i].id]);
          }
        });
        
        // uploader.bind("QueueChanged", function(up) {
        //   uploader.refresh();
        // });

        // uploader.bind("StateChanged", function(up) {
        //   uploader.refresh();
        // });

        // uploader.bind("Refresh", function(up) {
        //   // ???
        // });
        
        uploader.bind("Destroy", function(up) {
          var appletContainer;
          
          plupload.removeAllEvents(document.body, up.id);
          
          delete initialized[up.id];
          delete uploadInstances[up.id];
          
          appletContainer = document.getElementById(up.id + '_applet_container');
          if (appletContainer) {
            container.removeChild(appletContainer);
          }
          appletContainer = null;
        });
        
        callback({success : true});
      });
    }
  });
})(window, document, plupload);
