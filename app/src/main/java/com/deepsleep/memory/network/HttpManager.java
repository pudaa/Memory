package com.deepsleep.memory.network;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.content.Context;
import android.net.Uri;
import com.deepsleep.memory.handle_utils.BitmapManager;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import android.graphics.Bitmap;
import android.util.Log;

public class HttpManager {
	/*
	 * 这是一个http请求类，定义了无参数，1个参数，2个参数，多个参数访问接口的方法
	 */

	public static String doHttpGetNoPara(String url) {// 不传参数，只根据url地址访问接口
		try {
			HttpGet request = new HttpGet(url);

			// request.addHeader(headerKey, headerValue);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static String doHttpGetOneHeader(String url, String headerKey, String headerValue) {// 根据url访问接口，传递1个参数，包括参数名和参数值
		try {
			HttpGet request = new HttpGet(url);
			request.addHeader(headerKey, headerValue);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");

				return result;
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static String doHttpGetTwoHeader(String url, String headerKey, // 根据url访问接口，传递2个参数，包括参数名和参数值
			String headerValue, String headerKey1, String headerValue1) {
		try {
			HttpGet request = new HttpGet(url);
			request.addHeader(headerKey, headerValue);
			request.addHeader(headerKey1, headerValue1);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static Bitmap downloadBitmap(String webUri, String iconId) {// 根据课程图片的url地址，和iconId下载图片
		Bitmap bitmap = null;
		try {

			HttpGet request = new HttpGet(webUri);

			request.addHeader("iconId", iconId);
			Log.i("HttpManager", "---iconId----" + iconId);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);

			int statusCode = 0;
			statusCode = httpResponse.getStatusLine().getStatusCode();
			Log.i("HttpManager", "---statusCode----" + statusCode);
			if (statusCode == 200) {

				HttpEntity entity = httpResponse.getEntity();
				Log.i("HttpManager", "-------");
				if (null != entity) {
					InputStream is = entity.getContent();
					byte[] data = BitmapManager.readStream(is);
					if (data != null) {
						bitmap = BitmapManager.decodebitmap(data);

						return bitmap;
					}
				}
			} else {
				Log.i("HttpManager", "--bitmap--statusCode---" + statusCode);
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return bitmap;
	}

	public static Bitmap downloadBitmapByURL(String webUri) {// 根据课程图片的url地址，和iconId下载图片
		Bitmap bitmap = null;
		try {

			HttpGet request = new HttpGet(webUri);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);

			int statusCode = 0;
			statusCode = httpResponse.getStatusLine().getStatusCode();
			Log.i("HttpManager", "---statusCode----" + statusCode);
			if (statusCode == 200) {

				HttpEntity entity = httpResponse.getEntity();
				Log.i("HttpManager", "-------");
				if (null != entity) {
					InputStream is = entity.getContent();
					byte[] data = BitmapManager.readStream(is);
					if (data != null) {
						bitmap = BitmapManager.decodebitmap(data);

						return bitmap;
					}
				}
			} else {
				Log.i("HttpManager", "--bitmap--statusCode---" + statusCode);
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return bitmap;
	}

	public static String doHttpGetThreeHeader(String url, String headerKey, String headerValue, String headerKey1,
			String headerValue1, String headerKey2, String headerValue2) {
		try {
			HttpGet request = new HttpGet(url);
			request.addHeader(headerKey, headerValue);
			request.addHeader(headerKey1, headerValue1);
			request.addHeader(headerKey2, headerValue2);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static String doHttpGetFourPara(String url, String headerKey, String headerValue, String headerKey1,
			String headerValue1, String headerKey2, String headerValue2, String headerKey3, String headerValue3) {
		try {
			HttpGet request = new HttpGet(url);
			request.addHeader(headerKey, headerValue);
			request.addHeader(headerKey1, headerValue1);
			request.addHeader(headerKey2, headerValue2);
			request.addHeader(headerKey3, headerValue3);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			System.out.println(
					"httpResponse.getStatusLine().getStatusCode()" + httpResponse.getStatusLine().getStatusCode());
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static String doHttpGetFivePara(String url, String headerKey, String headerValue, String headerKey1,
			String headerValue1, String headerKey2, String headerValue2, String headerKey3, String headerValue3,
			String headerKey4, String headerValue4) {
		try {
			HttpGet request = new HttpGet(url);
			request.addHeader(headerKey, headerValue);
			request.addHeader(headerKey1, headerValue1);
			request.addHeader(headerKey2, headerValue2);
			request.addHeader(headerKey3, headerValue3);
			request.addHeader(headerKey4, headerValue4);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static String doHttpPost(String url, String headerKey, String headerValue, JSONObject param) {
		try {
			HttpPost request = new HttpPost(url);
			request.addHeader("Content-Type", "application/json");
			request.addHeader(headerKey, headerValue);
			StringEntity entity;
			entity = new StringEntity(param.toString());
			request.setEntity(entity);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static String doHttpPostThreeParam(String url, String headerKey1, String headerValue1, String headerKey2,
			String headerValue2, String headerKey3, String headerValue3) {
		try {
			HttpPost request = new HttpPost(url);
			request.addHeader("Content-Type", "application/json");
			request.addHeader(headerKey1, headerValue1);
			request.addHeader(headerKey2, headerValue2);
			request.addHeader(headerKey3, headerValue3);
			// StringEntity entity;
			// entity = new StringEntity(param.toString());
			// request.setEntity(entity);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	/**
	 *
	 * 
	 * @param url
	 * @param param
	 * @return
	 */
	public static String doHttpPost(String url, JSONObject param) {
		try {
			HttpPost request = new HttpPost(url);
			request.addHeader("Content-Type", "application/json");
			StringEntity entity;
			entity = new StringEntity(param.toString(), "UTF-8");
			request.setEntity(entity);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * PUT 请求，发送 JSON body
	 */
	public static String doHttpPut(String url, JSONObject param) {
		try {
			HttpPut request = new HttpPut(url);
			request.addHeader("Content-Type", "application/json");
			StringEntity entity;
			entity = new StringEntity(param.toString(), "UTF-8");
			request.setEntity(entity);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String doHttpPostWithJson(String url, JSONObject param, String headerKey1, String headerValue1) {
		try {
			HttpPost request = new HttpPost(url);
			request.addHeader("Content-Type", "application/json");
			StringEntity entity;
			entity = new StringEntity(param.toString(), "UTF-8");
			request.setEntity(entity);
			request.addHeader(headerKey1, headerValue1);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static String doHttpPostOneHeader(String url, String headerKey1, String headerValue1) {
		try {
			HttpPost request = new HttpPost(url);
			request.addHeader("Content-Type", "application/json");
			request.addHeader(headerKey1, headerValue1);
			// StringEntity entity;
			// entity = new StringEntity(param.toString());
			// request.setEntity(entity);
			Log.i("UserAPI", "--------------run()--request------------" + request.toString());
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				Log.i("UserAPI", "--------------run()--result------------" + result.toString());
				return result;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static String doHttpPostTwoHeader(String url, String headerKey1, String headerValue1, String headerKey2,
			String headerValue2) {
		try {
			HttpPost request = new HttpPost(url);
			request.addHeader("Content-Type", "application/json");
			request.addHeader(headerKey1, headerValue1);
			request.addHeader(headerKey2, headerValue2);
			// StringEntity entity;
			// entity = new StringEntity(param.toString());
			// request.setEntity(entity);
			Log.i("UserAPI", "--------------run()--request------------" + request.toString());
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				Log.i("UserAPI", "--------------run()--result------------" + result.toString());
				return result;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static String doHttpPostFourHeader(String url, String headerKey1, String headerValue1, String headerKey2,
			String headerValue2, String headerKey3, String headerValue3, String headerKey4, String headerValue4) {
		try {
			HttpPost request = new HttpPost(url);
			request.addHeader("Content-Type", "application/json");
			request.addHeader(headerKey1, headerValue1);
			request.addHeader(headerKey2, headerValue2);
			request.addHeader(headerKey3, headerValue3);
			request.addHeader(headerKey4, headerValue4);
			StringEntity entity;
			// entity = new StringEntity(param.toString());
			// request.setEntity(entity);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public static String doHttpPostFiveHeader(String url, String headerKey1, String headerValue1, String headerKey2,
			String headerValue2, String headerKey3, String headerValue3, String headerKey4, String headerValue4,
			String headerKey5, String headerValue5) {
		try {
			HttpPost request = new HttpPost(url);
			request.addHeader("Content-Type", "application/json");
			request.addHeader(headerKey1, headerValue1);
			request.addHeader(headerKey2, headerValue2);
			request.addHeader(headerKey3, headerValue3);
			request.addHeader(headerKey4, headerValue4);
			request.addHeader(headerKey5, headerValue5);
			StringEntity entity;
			// entity = new StringEntity(param.toString());
			// request.setEntity(entity);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * post���� ������json����
	 * 
	 * @param url
	 * @param
	 * @return
	 */

	public static String doHttpPost(String url) { //
		try {
			HttpPost request = new HttpPost(url);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				Log.i("doHttpPost", "--------------doHttpPost()--result------------" + result);
				return result;
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block

			e.printStackTrace();
		} catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static String doHttpGetWithParams(String url, JSONObject params) {
		try {
			// 构建带参数的URL
			List<NameValuePair> paramList = new ArrayList<NameValuePair>();
			if (params != null) {
				@SuppressWarnings("unchecked")
				java.util.Iterator<String> keys = params.keys();
				while (keys.hasNext()) {
					String key = keys.next();
					paramList.add(new BasicNameValuePair(key, params.optString(key)));
				}
			}

			String paramString = URLEncodedUtils.format(paramList, "UTF-8");
			String finalUrl = url;
			if (paramString != null && !paramString.isEmpty()) {
				finalUrl = url + "?" + paramString;
			}

			HttpGet request = new HttpGet(finalUrl);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String doHttpPostWithParams(String url, JSONObject params) { // 带参数的POST请求
		try {
			HttpPost request = new HttpPost(url);
			request.addHeader("Content-Type", "application/x-www-form-urlencoded");

			// 添加POST参数
			List<NameValuePair> paramList = new ArrayList<NameValuePair>();
			if (params != null) {
				@SuppressWarnings("unchecked")
				java.util.Iterator<String> keys = params.keys();
				while (keys.hasNext()) {
					String key = keys.next();
					paramList.add(new BasicNameValuePair(key, params.optString(key)));
				}
			}

			if (!paramList.isEmpty()) {
				UrlEncodedFormEntity entity = new UrlEncodedFormEntity(paramList, "UTF-8");
				request.setEntity(entity);
			}

			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String doHttpPostWithTextBody(String url, String headerKey, String headerValue, String textBody) {
		try {
			HttpPost request = new HttpPost(url);
			request.addHeader("Content-Type", "text/plain; charset=UTF-8");
			request.addHeader(headerKey, String.valueOf(headerValue));

			StringEntity entity = new StringEntity(textBody, "UTF-8");
			request.setEntity(entity);

			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				String result = EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
				return result;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String doHttpPostWithImageUri(String url, Uri imageUri, Context context) {
		String boundary = "----" + UUID.randomUUID().toString();
		String lineEnd = "\r\n";
		String twoHyphens = "--";

		try {
			Log.i("HttpManager", "doHttpPostWithImageUri → " + url);
			URL urlObj = new URL(url);
			HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();

			// 超时设置（连接 15s，读取 30s）
			connection.setConnectTimeout(15000);
			connection.setReadTimeout(30000);

			// 设置请求方法和属性
			connection.setRequestMethod("POST");
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setRequestProperty("Connection", "Keep-Alive");
			connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

			// 写入数据
			DataOutputStream dos = new DataOutputStream(connection.getOutputStream());

			// 从Uri读取图片数据
			InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
			if (inputStream == null) {
				Log.e("HttpManager", "无法打开图片 URI: " + imageUri);
				return null;
			}
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				byteArrayOutputStream.write(buffer, 0, bytesRead);
			}
			byte[] imageData = byteArrayOutputStream.toByteArray();
			Log.i("HttpManager", "图片大小: " + imageData.length + " bytes");

			inputStream.close();
			byteArrayOutputStream.close();

			// 写入文件部分
			dos.writeBytes(twoHyphens + boundary + lineEnd);
			dos.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"cropped_image.jpg\"" + lineEnd);
			dos.writeBytes("Content-Type: image/jpeg" + lineEnd);
			dos.writeBytes(lineEnd);

			// 写入图片数据
			dos.write(imageData);

			dos.writeBytes(lineEnd);
			dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

			// 关闭流
			dos.flush();
			dos.close();

			// 获取响应
			int responseCode = connection.getResponseCode();
			Log.i("HttpManager", "响应码: " + responseCode);

			if (responseCode == 200) {
				InputStream is = connection.getInputStream();
				StringBuilder response = new StringBuilder();
				byte[] data = new byte[1024];
				int count;
				while ((count = is.read(data)) != -1) {
					response.append(new String(data, 0, count));
				}
				is.close();
				String result = response.toString();
				Log.i("HttpManager", "响应体: " + result);
				return result;
			} else {
				// 读取服务端错误信息
				InputStream es = connection.getErrorStream();
				if (es != null) {
					StringBuilder errBody = new StringBuilder();
					byte[] errData = new byte[1024];
					int errCount;
					while ((errCount = es.read(errData)) != -1) {
						errBody.append(new String(errData, 0, errCount));
					}
					es.close();
					Log.e("HttpManager", "服务端错误 (" + responseCode + "): " + errBody);
				} else {
					Log.e("HttpManager", "服务端错误码 " + responseCode + "，无错误响应体");
				}
			}
		} catch (Exception e) {
			Log.e("HttpManager", "图片上传异常: " + e.getMessage(), e);
		}

		return null;
	}

	public static String doHttpPostWithImageAndParams(String url, Context context, Uri imageUri, String headerKey,
			String headerValue) {
		try {
			URL urlObj = new URL(url);
			HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();

			// 设置请求方法和属性
			connection.setRequestMethod("POST");
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setRequestProperty("Connection", "Keep-Alive");

			String boundary = "----FormBoundary" + System.currentTimeMillis();
			connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
			// 设置自定义请求头（userId等）
			connection.setRequestProperty(headerKey, headerValue);

			OutputStream outputStream = connection.getOutputStream();
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

			// 从Uri读取图片数据
			InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				byteArrayOutputStream.write(buffer, 0, bytesRead);
			}
			byte[] imageData = byteArrayOutputStream.toByteArray();
			inputStream.close();
			byteArrayOutputStream.close();

			// 根据图片数据确定MIME类型和文件扩展名
			String mimeType = "image/jpeg";
			String fileExtension = ".jpg";

			if (imageData.length > 0) {
				if (imageData.length > 3 && (imageData[0] & 0xFF) == 0xFF && (imageData[1] & 0xFF) == 0xD8
						&& (imageData[2] & 0xFF) == 0xFF) {
					mimeType = "image/jpeg";
					fileExtension = ".jpg";
				} else if (imageData.length > 8 && (imageData[0] & 0xFF) == 0x89 && (imageData[1] & 0xFF) == 0x50
						&& (imageData[2] & 0xFF) == 0x4E && (imageData[3] & 0xFF) == 0x47) {
					mimeType = "image/png";
					fileExtension = ".png";
				} else if (imageData.length > 6 && (imageData[0] & 0xFF) == 0x47 && (imageData[1] & 0xFF) == 0x49
						&& (imageData[2] & 0xFF) == 0x46) {
					mimeType = "image/gif";
					fileExtension = ".gif";
				}
			}

			// 写入multipart文件部分头部
			writer.append("--").append(boundary).append("\r\n");
			writer.append("Content-Disposition: form-data; name=\"image\"; filename=\"cropped_image")
					.append(fileExtension).append("\"").append("\r\n");
			writer.append("Content-Type: ").append(mimeType).append("\r\n");
			writer.append("\r\n");
			writer.flush();

			// 写入图片二进制数据
			outputStream.write(imageData);
			outputStream.flush();

			// 写入multipart结束标记
			writer.append("\r\n");
			writer.append("--").append(boundary).append("--").append("\r\n");
			writer.close();

			// 获取响应
			int responseCode = connection.getResponseCode();
			Log.i("HttpManager", "doHttpPostWithImageAndParams responseCode: " + responseCode);
			if (responseCode == HttpURLConnection.HTTP_OK) {
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
				StringBuilder response = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					response.append(line);
				}
				reader.close();
				return response.toString();
			} else {
				// 读取错误流以便调试
				InputStream errorStream = connection.getErrorStream();
				if (errorStream != null) {
					BufferedReader errorReader = new BufferedReader(
							new InputStreamReader(errorStream, StandardCharsets.UTF_8));
					StringBuilder errorResponse = new StringBuilder();
					String line;
					while ((line = errorReader.readLine()) != null) {
						errorResponse.append(line);
					}
					errorReader.close();
					Log.e("HttpManager", "Server error response: " + errorResponse.toString());
				}
			}
		} catch (Exception e) {
			Log.e("HttpManager", "doHttpPostWithImageAndParams exception", e);
		}

		return null;
	}

	public static String doHttpPostWithAudioAndText(String urlString, Uri audioUri, String referenceText,
			Context context) {
		try {
			URL url = new URL(urlString);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);

			String boundary = "----FormBoundary" + System.currentTimeMillis();
			connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

			OutputStream outputStream = connection.getOutputStream();
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

			// 写入参考文本字段
			writer.append("--").append(boundary).append("\r\n");
			writer.append("Content-Disposition: form-data; name=\"referenceText\"").append("\r\n");
			writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n\r\n");
			writer.append(referenceText).append("\r\n");
			writer.flush();

			// 写入音频文件字段
			writer.append("--").append(boundary).append("\r\n");
			writer.append("Content-Disposition: form-data; name=\"audio\"; filename=\"audio.wav\"").append("\r\n");
			writer.append("Content-Type: audio/wav").append("\r\n\r\n");
			writer.flush();

			// 从Uri读取音频数据并写入输出流
			InputStream inputStream = context.getContentResolver().openInputStream(audioUri);
			byte[] buffer = new byte[4096];
			int bytesRead;
			while ((bytesRead = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, bytesRead);
			}
			outputStream.flush();
			inputStream.close();

			writer.append("\r\n").flush();
			writer.append("--").append(boundary).append("--").append("\r\n");
			writer.close();

			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				StringBuilder response = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					response.append(line);
				}
				reader.close();
				return response.toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 通用 multipart/form-data POST 请求（支持多个文本字段 + 可选文件 + header）
	 */
	public static String doHttpPostMultipart(String urlString, java.util.Map<String, String> textFields,
			String fileFieldName, Uri fileUri, String fileName, String fileMimeType, String headerKey,
			String headerValue, Context context) {
		try {
			URL url = new URL(urlString);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			if (headerKey != null && headerValue != null) {
				connection.setRequestProperty(headerKey, headerValue);
			}

			String boundary = "----FormBoundary" + System.currentTimeMillis();
			connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

			OutputStream outputStream = connection.getOutputStream();
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

			// 写入所有文本字段
			if (textFields != null) {
				for (java.util.Map.Entry<String, String> entry : textFields.entrySet()) {
					writer.append("--").append(boundary).append("\r\n");
					writer.append("Content-Disposition: form-data; name=\"").append(entry.getKey()).append("\"")
							.append("\r\n");
					writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n\r\n");
					writer.append(entry.getValue() != null ? entry.getValue() : "").append("\r\n");
					writer.flush();
				}
			}

			// 写入文件字段（可选）
			if (fileFieldName != null && fileUri != null && context != null) {
				writer.append("--").append(boundary).append("\r\n");
				writer.append("Content-Disposition: form-data; name=\"").append(fileFieldName).append("\"; filename=\"")
						.append(fileName != null ? fileName : "audio.wav").append("\"").append("\r\n");
				writer.append("Content-Type: ").append(fileMimeType != null ? fileMimeType : "audio/wav")
						.append("\r\n\r\n");
				writer.flush();

				InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, bytesRead);
				}
				outputStream.flush();
				inputStream.close();

				writer.append("\r\n").flush();
			}

			writer.append("--").append(boundary).append("--").append("\r\n");
			writer.close();

			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				StringBuilder response = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					response.append(line);
				}
				reader.close();
				return response.toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * POST JSON → 下载 WAV 音频到本地文件，返回文件路径
	 */
	public static String doHttpPostDownloadWav(String urlString, JSONObject jsonParam, Context context) {
		try {
			URL url = new URL(urlString);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setDoOutput(true);
			conn.setConnectTimeout(15000);
			conn.setReadTimeout(30000);

			OutputStream os = conn.getOutputStream();
			os.write(jsonParam.toString().getBytes(StandardCharsets.UTF_8));
			os.flush();
			os.close();

			if (conn.getResponseCode() == 200) {
				File dir = new File(context.getExternalFilesDir(null), "Audio");
				if (!dir.exists())
					dir.mkdirs();
				File outFile = new File(dir, "tts_welcome_" + System.currentTimeMillis() + ".wav");
				FileOutputStream fos = new FileOutputStream(outFile);
				InputStream is = conn.getInputStream();
				byte[] buf = new byte[4096];
				int len;
				while ((len = is.read(buf)) != -1)
					fos.write(buf, 0, len);
				fos.close();
				is.close();
				return outFile.getAbsolutePath();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/** DELETE 请求，带一个 Header */
	public static String doHttpDelete(String url, String headerKey, String headerValue) {
		try {
			HttpDelete request = new HttpDelete(url);
			request.addHeader(headerKey, headerValue);
			HttpResponse httpResponse = new DefaultHttpClient().execute(request);
			if (httpResponse.getStatusLine().getStatusCode() == 200) {
				return EntityUtils.toString(httpResponse.getEntity(), "UTF_8");
			}
		} catch (Exception e) {
			Log.e("HttpManager", "doHttpDelete error", e);
		}
		return null;
	}
}
