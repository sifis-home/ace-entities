package eu.sifishome.json.incoming;

// {
//   "Volatile": {
//     "value": {
//       "message": {
//         "scope": "type string",
//         "audience": "type string",
//         "address": "type string"
//       },
//       "topic": "type string"
//     }
//   }
// }

public class Volatile {

    private InValue value;

    public InValue getValue() {
        return value;
    }

}
