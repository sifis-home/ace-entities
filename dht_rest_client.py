import requests

api_url = "http://localhost:3000/"

# To install script requirements run:
# pip install -r python_requirements.txt
# Codebase for DHT and original example script:
# https://github.com/domo-iot/sifis-dht-test

valTarget = input("Enter a topic name to use for publishing (default: \"command_ace_ucs\"): ")
valScope = input("Enter scope to send (default: \"r_temp r_helloWorld\"): ")
valAudience = input("Enter audience to send (default: \"rs1\"): ")
valAddress = input("Enter address to send (\"coap://localhost:5685\"): ")

if valScope in ['']:
    valScope = "r_temp r_helloWorld"
if valAudience in ['']:
    valAudience = "rs1"
if valAddress in ['']:
    valAddress = "coap://localhost:5685"

if valTarget in ['']:
    topic = "command_ace_ucs"

# else:
#     print("Invalid command")
#     quit()

#publish volatile message
volatile_message = {"message": {"scope": valScope, "audience": valAudience, "address": valAddress}, "topic": topic }
response = requests.post(api_url + "pub", json=volatile_message)
print(response.json())

