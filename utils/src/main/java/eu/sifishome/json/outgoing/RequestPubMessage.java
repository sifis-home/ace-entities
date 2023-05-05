package eu.sifishome.json.outgoing;

// {
//   "RequestPubMessage": {
//     "value": {
//       "message":"type string",
//       "topic":"type string"
//     }
//   }
// }

public class RequestPubMessage {

	private OutValue value;

	public RequestPubMessage() {

	}

	public void setValue(OutValue value) {
		this.value = value;
	}

	public OutValue getValue() {
		return value;
	}
}
