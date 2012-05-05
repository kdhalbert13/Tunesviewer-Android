package com.tunes.viewer.WebView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.tunes.viewer.ItunesXmlParser;
import com.tunes.viewer.R;
import com.tunes.viewer.TunesViewerActivity;
import com.tunes.viewer.FileDownload.DownloadService;
import com.tunes.viewer.R.string;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.ClipboardManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Javascript interface for the WebView
 * Note that all of these functions must be safe for untrusted input!
 * They are called by the injected script found in res/raw/javascript.js.
 */
public class JSInterface {

	private static final String TAG = "JS-Interface";
	private TunesViewerActivity _context;
	final String[] audioFormats = {".mp3",".m4a",".amr",".m4p",".aiff",".aif",".aifc"};
	final String[] videoFormats = {".mp4",".m4v",".mov",".m4b"};
	public JSInterface(TunesViewerActivity c) {
		_context = c;
	}
	
	/**
	 * Starts a media file download.
	 * @param title - Title of this media
	 * @param podcast - Name of the podcast this belongs to.
	 * @param url - String for download url.
	 */
	public void download(String title, String podcast, String url) {
		if (title==null) {
			title="Unknown";
		}
		if (podcast==null) {
			 title="Unknown";
		}
		Intent intent = new Intent(_context,DownloadService.class);
		intent.putExtra("url", url);
		intent.putExtra("podcast", podcast);
		intent.putExtra("name",title);
		_context.startService(intent);
	}
	
	/**
	 * Previews an audio/video stream using the system's default player.
	 * @param title
	 * @param url
	 */
	public void preview(String title, String url) {
		try {
			url = doRedirect(url);
			Intent i = new Intent(Intent.ACTION_VIEW);
			String type = ItunesXmlParser.fileExt(url);
			if (Arrays.asList(audioFormats).indexOf(type) > -1) {
				i.setDataAndType(Uri.parse(url), "audio/*");
			} else if (Arrays.asList(videoFormats).indexOf(type) > -1) {
				i.setDataAndType(Uri.parse(url), "video/*");
			} else {
				i.setDataAndType(Uri.parse(url), "*/*");
			}
			_context.startActivity(i);

			
		} catch (ActivityNotFoundException e) {
			Toast.makeText(_context, _context.getText(R.string.NoActivity), Toast.LENGTH_LONG).show();
		}
	}
	
	/**
	 * Fix access-denied redirection failure for preview.
	 * @param url
	 * @return working url
	 */
	private String doRedirect(String url) {
		String output = url;
		BufferedReader in = null;
		try {
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();
			request.setHeader("User-agent", "stagefright/1.2.0");
			request.setURI(new URI(url));
			HttpResponse response = client.execute(request);
			
			Log.i(TAG,"response "+response.getStatusLine().getStatusCode()/100);
			if (response.getStatusLine().getStatusCode()/100 != 2) {
				//Probably failed redirect.
				in = new BufferedReader(
						new InputStreamReader(response.getEntity().getContent()),1024*4);
				StringBuffer sb = new StringBuffer();
				String line = "";
				while ((line = in.readLine()) != null) {
					sb.append(line+"\n");
				}
				SAXParserFactory factory = SAXParserFactory.newInstance();
				factory.setValidating(false);
				SAXParser saxParser= factory.newSAXParser();
				Gettext parser = new Gettext();
				XMLReader xr = saxParser.getXMLReader();
				//Use the response html, with <p> replaced because it breaks xml:
				InputSource is = new InputSource(new StringReader(sb.toString().replace("<P>", "").replace("<p>", "")));
				xr.setContentHandler(parser);
				xr.parse(is);
				System.out.println(parser.out);
				int last = parser.out.lastIndexOf("\" on this server.\nReference #");
				int first = parser.out.indexOf("http:");
				if (last != -1 && first != -1) {
					String urlfail = parser.out.substring(first,last);
					Pattern p = Pattern.compile("http://deimos3.apple.com/([a-zA-Z]*)/(.*)");
					Matcher m = p.matcher(urlfail);
					if (m.find()) {
						//Last part of the url is direct url:
						output = "http://"+m.group(2);
					}
				}
			}
			//Toast.makeText(_context, conn.getContentType(), 1000).show();
			//if(conn.getContentEncoding())
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (IOException e) {
			} catch (NullPointerException e) {
			}
		}
		return output;
	}
	

	/**
	 * Shows a view-source dialog with given source string.
	 * @param src
	 */
	public void source(String src) {
		final String source = src;
		new AlertDialog.Builder(_context)
		.setIcon(android.R.drawable.ic_dialog_info)
		.setTitle("Page Source ("+source.length()+" chars.)")
		.setMessage(source)
		.setPositiveButton("Copy Text", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				ClipboardManager cb = (ClipboardManager)_context.getSystemService(Context.CLIPBOARD_SERVICE);
				cb.setText(source);
			}
		})
		.setNegativeButton("Close", null)
		.show();
	}
	
	/**
	 * Go to a url. Workaround for http://stackoverflow.com/questions/5129112/shouldoverrideurlloading-does-not-work-catch-link-clicks-while-page-is-loading
	 * @param url - the url to go to.
	 */
	public void go(String url) {
		_context.loadUrl(url);
	}
	
	/**
	 * Tries to subscribe using a podcatcher.
	 * @param url
	 */
	public void subscribe(String url) {
		try {
			//Change it to feed or itpc://url, to send automatically to podcatcher.
			if (!url.startsWith("feed") && url.indexOf("://")>-1) {
				url = "feed"+url.substring(url.indexOf("://"));
			}
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			i.setData(Uri.parse(url));
			_context.startActivity(i);
		} catch (ActivityNotFoundException e) {
			new AlertDialog.Builder(_context)
			.setIcon(android.R.drawable.ic_dialog_alert)
			.setTitle("No Podcatcher")
			.setMessage("No podcast app found to handle this link! You must install a podcast manager app that handles itpc:// links, to subscribe.")
			.setNegativeButton("OK", null)
			.show();
		}
	}
	
	/**
	 * Sets the activity's title.
	 * @param title
	 */
	public void setTitle(final String title) {
		_context.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				_context.setTitle(title.replace("&amp;","&"));
			}
		});
	}
}

/**
 * Simple helper class to parse textcontent of an xml. For example, "<xml>&#58;&#47;&#47;</xml>" should return "://".
 */
class Gettext extends DefaultHandler {
	public StringBuffer out;
	
	public Gettext() {
		out = new StringBuffer();
	}
	
	public void characters(char[] buffer, int start, int length) {
		out.append(buffer, start, length);
	}
}
