package eslDemo;

public abstract interface MqttEventNotify {
	public enum ConnectionNotify
	{
		CONN_NTF_CONNECED,    //conn mqtt srv success
		CONN_SHAKE_SUCCESS,   //shake with gateway success
		CONN_NTF_DISCONNECTED
	};
	
	public enum ActionNotify
	{
		MSG_DEVICE_NOT_FOUND,
		MSG_DOWNLOAD_SUCCESS,   //message download to gateway success
		MSG_EXECUTE_SUCCESS,     //message execute in esl success
		MSG_EXECUTE_FAIL,
		
		FOUND_DEVICE,
		DEVICE_UPDATE,
		MSG_SHAKE_REQs
	};
	
	public abstract void connectionNotify(ConnectionNotify connNtf);
		
	public abstract void actionNotify(ActionNotify downNtf, String strDeviceMac, Object notifyObj);
}
