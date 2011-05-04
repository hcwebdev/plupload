#!/usr/bin/env python
import os
from werkzeug import run_simple
from werkzeug import Request
from werkzeug import Response
from werkzeug import secure_filename
from werkzeug.exceptions import BadRequest
from werkzeug.exceptions import HTTPException
from werkzeug._internal import _log


os.environ['SERVER_SOFTWARE'] = 'development'
os.environ['REMOTE_USER'] = 'defaultuser'
os.environ['ROOT_PATH'] = os.path.abspath(os.path.dirname(os.path.dirname(__file__)))

ROOT_PATH = os.environ['ROOT_PATH']
STATIC_FILES = {
    '/js_dev':  os.path.join(ROOT_PATH, 'src/javascript'),
    '/js':  os.path.join(ROOT_PATH, 'js'),
    '/': os.path.join(ROOT_PATH, 'examples')
}
UPLOAD_DIR = os.path.join(ROOT_PATH, 'uploads')
DUMP_HTML = '''
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" dir="ltr">
<head>
<meta http-equiv="content-type" content="text/html; charset=UTF-8"/>
<link rel="stylesheet" href="css/plupload.css" type="text/css" media="screen" />
<title>Plupload - Form dump</title>
<style type="text/css">
  body {
      font-family:Verdana, Geneva, sans-serif;
      font-size:13px;
      color:#333;
      background:url(bg.jpg);
  }
</style>
</head>
<body>

<h1>Post dump</h1>

<p>Shows the form items posted.</p>

<table>
    <tr>
      <th>Name</th>
      <th>Value</th>
    </tr>
    %(rows)s
</table>

</body>
</html>
'''
DUMP_ROW_HTML = '''
<tr class="%(cssclass)s">
  <td>%(param)s</td>
  <td>%(value)s</td>
</tr>
'''

def log(msg):
    _log('info', msg)

class Server(object):
    
    def clean_filename(self, filename):
        parts = filename.split(".")
        if len(parts) > 1:
            filename = '.'.join(parts[0:-1] + [parts[-1].lower()])
        return secure_filename(filename)

    def get_or_create_file(self, chunk, dst):
        if chunk == 0:
            f = file(dst, 'wb')
        else:
            f = file(dst, 'ab')
        return f
    
    def streaming_upload(self, request):
        """
        Save application/octet-stream request to file. We expect that
        the file name has been passed in the querystring, so will be
        present in request.args (same for chunk and chunks)
        """
        log('received application/octet-stream request')
        try:
            filename = self.clean_filename(request.args['name'])
            dst = os.path.join(UPLOAD_DIR, filename)
            chunk = int(request.args.get('chunk', 0))
            # chunks = int(request.args.get('chunks', 0))
            buf = request.stream.read(request.content_length)
            f = self.get_or_create_file(chunk, dst)
            f.write(buf)
            f.close()
        except Exception, e:
            log('exception: %s' % str(e))
            raise e
    
    def multipart_upload(self, request):
        """
        Save multipart/form-data request to file.  Some runtimes will
        pass the file name, chunk, and chunks values via the posted data, 
        while others will pass it as part of the querystring.
        """
        log('received multipart/form-data request')
        try:
            filename = self.clean_filename(
                request.form.get('name', request.args.get('name'))
            )
            dst = os.path.join(UPLOAD_DIR, filename)
            chunk = int(
                request.form.get('chunk', request.args.get('chunk', 0))
            )
            chunks = int(
                request.form.get('chunks', request.args.get('chunks', 0))
            )
            
            log('filename: %s' % filename)
            log('chunk: %s' % chunk)
            log('chunks: %s' % chunks)
            log('content length: %s' % request.content_length)
            f = self.get_or_create_file(chunk, dst)
            file = request.files['file']
            for b in file:
                f.write(b)
            f.close()
        
            if chunk >= chunks - 1 and hasattr(file, 'filename') and file.filename:
                realname = self.clean_filename(file.filename)
                log('real filename: %s' % realname)
                newdst = os.path.join(UPLOAD_DIR, realname)
                if newdst != dst:
                    os.rename(dst, newdst)
        except Exception, e:
            log('exception: %s' % str(e))
            raise e

    def handle(self, request):
        """
        Handle uploads from the different runtimes.
        """
        if request.method != "POST":
            raise BadRequest("only posts allowed")
        
        if request.path == '/dump':
            return self.dump(request)
        
        if request.content_type.lower() == 'application/octet-stream':
            self.streaming_upload(request)
        else:
            self.multipart_upload(request)

        return Response('uploaded')
    
    def dump(self, request):
        """
        Display the form items posted.
        """
        rows = []
        for idx, k in enumerate(request.form.keys()):
            rows.append(DUMP_ROW_HTML % {
                'cssclass': (idx % 2) and 'alt' or '',
                'param': k,
                'value': request.form[k],
            })
        return Response(DUMP_HTML % {'rows': '\n'.join(rows)}, content_type='text/html')


@Request.application
def app(request):
    try:
        server = Server()
        return server.handle(request)
    except HTTPException, e:
        return e

if __name__ == '__main__':
    if not os.path.exists(UPLOAD_DIR):
        os.mkdir(UPLOAD_DIR)
    run_simple('127.0.0.1', 
               8080, 
               app, 
               use_debugger=True, 
               use_reloader=True, 
               threaded=False, 
               processes=1, 
               static_files=STATIC_FILES)

