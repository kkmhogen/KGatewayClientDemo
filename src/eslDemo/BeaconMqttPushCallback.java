package eslDemo;


import java.util.HashMap;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import eslDemo.MqttConnNotify.ActionNotify;
import eslDemo.MqttConnNotify.ConnectionNotify;

public class BeaconMqttPushCallback implements MqttCallback {  
    private static int DEF_REQ_DATA_MAX_LENGHT = 1024*10;
    BeaconMqttClient mClient;
    MqttConnNotify mMqttNotify;
    
    public static final int ERR_INVALID_INPUT = 1;
   	public static final int ERR_PARSE_SUCCESS = 0;
   	
   	private String mGatewaySubaction;    //gateway using this topic to receive command
   	private String mGatewayPubaction;    //gateway using this topic to send command ack
   	
   	public class EslObject
   	{
   		String mMacAddress;    //device id
   		String mAdvData;       //adv data
   		int mRssi;
   		long mLastUpdateMsec;  //report time
   		long mCommandCause;
   	};
   	private HashMap<String, EslObject> mDeviceMap = new HashMap<>();
    
    BeaconMqttPushCallback(BeaconMqttClient conn, MqttConnNotify mqttNotify){
    	mClient = conn;
    	mMqttNotify = mqttNotify;
    }
    
    public String getGatewaySubAction()
    {
    	return mGatewaySubaction;
    }

    public void connectionLost(Throwable cause) {  
        //connection lost, now reconnect
        System.err.println("MQTT client connection disconnected");
        mClient.setConnected(false);
        
        mGatewaySubaction = null;
        mGatewayPubaction = null;
        mDeviceMap.clear();
        
        mMqttNotify.connectionNotify(ConnectionNotify.CONN_NTF_DISCONNECTED);
    }  
    
    public void deliveryComplete(IMqttDeliveryToken token) {
        
    }

    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // subscribe后得到的消息会执行到这里面  
        handleMqttMsg(new String(message.getPayload()));
    }  
    
    
    protected void handleMqttMsg(String strMqttInfo)  {
		// TODO Auto-generated method stub	
		//parse jason object
		if (strMqttInfo == null){
			System.out.println("Receive invalid null data");
			return;
		}
		int nReqLen = strMqttInfo.length();			
		//parise request
		if (nReqLen > DEF_REQ_DATA_MAX_LENGHT){
			System.out.println("Receive an max length request, len:" + nReqLen);
			return;
		}

		parseJsonReq(strMqttInfo);
	}
		
	public static boolean isMacAddressValid(String strMacAddr)
	{
		if (strMacAddr == null || strMacAddr.length() != 12)
		{
			return false;
		}
		
		for (int j = 0; j < strMacAddr.length(); j++)
	    {
	    	char cMac = strMacAddr.charAt(j);
	    	if ((cMac >= '0' && cMac <= '9')
	    			|| (cMac >= 'A' && cMac <= 'F')
	    			|| (cMac >= 'a' && cMac <= 'f'))
	    	{
	    		continue;
	    	}
	    	else
	    	{
	    		return false;
	    	}
	    }
		
		return true;
	}
	
	public static final String DEF_BEACON_TYPE = "0";
	
	public int parseJsonReq(String strMqttInfo)
	{
		
		try 
		{
			JSONObject cmdReq = JSONObject.fromObject(strMqttInfo);
			if (cmdReq == null)
			{
				System.out.println("Connection to Mqtt server failed");
				return 0;
			}
			
			//message type
			String strDataType = cmdReq.getString("msg");
			if (strDataType.equalsIgnoreCase("advdata"))
			{
				return handleBeaconRpt(cmdReq);
			}
			else if (strDataType.equalsIgnoreCase("alive"))
			{
				return handleShakeReq(cmdReq);
			}
			else if (strDataType.equalsIgnoreCase("dAck"))
			{
				return handleDownloadAck(cmdReq);
			}
			else
			{
				System.out.println("unknown scan response data");
				return ERR_INVALID_INPUT;
			}
		} 
		catch (Exception e) 
		{
			e.printStackTrace();
			return ERR_INVALID_INPUT;
		}
	}
	
	public int handleShakeReq(JSONObject cmdReqAgent)
	{
		try 
		{
			//mac address
			String strGwAddress = cmdReqAgent.getString("gmac");
			if (strGwAddress == null)
			{
				return ERR_INVALID_INPUT;
			}
			strGwAddress = strGwAddress.toUpperCase();
			if (!isMacAddressValid(strGwAddress)){
				System.out.println("beacon mqtt input invalid error");
				return ERR_INVALID_INPUT;
			}
					
			//subaction
			String strNewSubTopic = cmdReqAgent.getString("subaction");
			if (strNewSubTopic == null || strNewSubTopic.equals(""))
			{
				System.out.println("unknown obj data");
				return ERR_INVALID_INPUT;
			}
			
			
			//pubaction
			String strNewPubTopic = cmdReqAgent.getString("pubaction");
			if (strNewPubTopic == null || strNewPubTopic.equals(""))
			{
				System.out.println("unknown obj data");
				return ERR_INVALID_INPUT;
			}
			
			if (mGatewayPubaction == null)
			{
				mGatewayPubaction = strNewPubTopic;
				mClient.reNewSubscribe(mGatewayPubaction);
				
				System.out.println("shake with Gateway success");
				mMqttNotify.connectionNotify(ConnectionNotify.CONN_SHAKE_SUCCESS);
			}
			
			mGatewaySubaction = strNewSubTopic;
			mGatewayPubaction = strNewPubTopic;
		}
		catch (Exception e) 
		{
			return ERR_INVALID_INPUT;
		}
		
		return ERR_PARSE_SUCCESS;
	}
	
	
	public int handleBeaconRpt(JSONObject cmdReqAgent)
	{		
		try 
		{
			//mac address
			String strGwAddress = cmdReqAgent.getString("gmac");
			strGwAddress = strGwAddress.toUpperCase();
			if (!isMacAddressValid(strGwAddress)){
				System.out.println("beacon mqtt input invalid error");
				return ERR_INVALID_INPUT;
			}
					
			//obj list
			JSONArray objArray = cmdReqAgent.getJSONArray("obj");
			if (objArray == null)
			{
				System.out.println("unknown obj data");
				return ERR_INVALID_INPUT;
			}
			

			//update mac
			for (int i = 0; i < objArray.size(); i++)
			{
				JSONObject obj = objArray.getJSONObject(i);
		
				//device mac address
				String strDevMac = obj.getString("dmac");
				strDevMac = strDevMac.toUpperCase();
				if (!isMacAddressValid(strDevMac)){
					System.out.println("beacon mqtt input invalid error");
					return ERR_INVALID_INPUT;
				}
				

			    //rssi
				String strRssi = obj.getString("rssi");
				int nRssi = 0;
				if (strRssi != null)
				{
				    nRssi = Integer.valueOf(strRssi);
					if (nRssi >= 10){
						System.out.println("beacon mqtt input invalid error");
						return ERR_INVALID_INPUT;
					}
				}
				
			
				EslObject eslObj = mDeviceMap.get(strDevMac);
				if (eslObj == null)
				{
					eslObj = new EslObject();
					eslObj.mMacAddress = strDevMac;
					eslObj.mRssi = nRssi;
					eslObj.mAdvData = obj.getString("data1");
					eslObj.mLastUpdateMsec = System.currentTimeMillis();
					
					mDeviceMap.put(strDevMac, eslObj);
					System.out.println("Found new ESL device, id is:" + strDevMac + ", Rssi:" + nRssi);
					
					mMqttNotify.actionNotify(MqttConnNotify.ActionNotify.FOUND_DEVICE, eslObj);
				}
				else
				{
					//update
					eslObj.mRssi = nRssi;
					eslObj.mAdvData = obj.getString("data1");
					eslObj.mLastUpdateMsec = System.currentTimeMillis();
				}
			}
		} 
		catch (Exception e) 
		{
			return ERR_INVALID_INPUT;
		}

		return ERR_PARSE_SUCCESS;
	}
	
	public int handleDownloadAck(JSONObject cmdReqAgent)
	{
		try 
		{
			//mac address
			String strDevMac = cmdReqAgent.getString("mac");
			if (strDevMac == null)
			{
				return ERR_INVALID_INPUT;
			}
			strDevMac = strDevMac.toUpperCase();
			if (!isMacAddressValid(strDevMac)){
				System.out.println("beacon mqtt input invalid error");
				return ERR_INVALID_INPUT;
			}
			
			//found device
			EslObject eslObj = this.mDeviceMap.get(strDevMac);
			if (eslObj == null)
			{
				this.mMqttNotify.actionNotify(ActionNotify.MSG_DEVICE_NOT_FOUND, null);
				return ERR_INVALID_INPUT;
			}
			
			String strResult = cmdReqAgent.getString("rslt");
			if (strResult == null)
			{
				return ERR_INVALID_INPUT;
			}
			
			String strCause = cmdReqAgent.getString("cause");
			if (strCause == null)
			{
				return ERR_INVALID_INPUT;
			}
			int nCause = Integer.valueOf(strCause);
			eslObj.mCommandCause = nCause;
			
			if (strResult.equals("succ"))
			{
				if (nCause == 1)
				{
					this.mMqttNotify.actionNotify(ActionNotify.MSG_DOWNLOAD_SUCCESS, eslObj);
				}
				else
				{
					this.mMqttNotify.actionNotify(ActionNotify.MSG_EXECUTE_SUCCESS, eslObj);
				}
			}
			else
			{
				this.mMqttNotify.actionNotify(ActionNotify.MSG_EXECUTE_FAIL, eslObj);
			}
		}
		catch (Exception e) 
		{
			return ERR_INVALID_INPUT;
		}
		
		return ERR_PARSE_SUCCESS;
	}
	
}