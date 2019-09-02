include 'asynchttp_v1'

definition(
	name: "MyQ",
	namespace: "stjeffrey",
	author: "Stephane Jeffrey",
	description: "MyQ Integration with Smartthings",
	category: "My Apps",
	iconUrl:   "https://raw.githubusercontent.com/stjeffrey/SmartThingsPublic/master/icons/myq.png",
	iconX2Url: "https://raw.githubusercontent.com/stjeffrey/SmartThingsPublic/master/icons/myq@2x.png",
	iconX3Url: "https://raw.githubusercontent.com/stjeffrey/SmartThingsPublic/master/icons/myq@3x.png"
)

preferences {
    page(name: "prefLogIn", title: "MyQ")
	page(name: "prefListDevices", title: "MyQ")
/*    page(name: "prefSensor1", title: "MyQ")
    page(name: "prefSensor2", title: "MyQ")
    page(name: "prefSensor3", title: "MyQ")
    page(name: "prefSensor4", title: "MyQ")
    page(name: "prefSensor5", title: "MyQ")
    page(name: "prefSensor6", title: "MyQ")
    page(name: "prefSensor7", title: "MyQ")
    page(name: "prefSensor8", title: "MyQ")
    page(name: "noDoorsSelected", title: "MyQ")
    page(name: "summary", title: "MyQ") */
    page(name: "prefUninstall", title: "MyQ")
}

/* Preferences */
def preferences_Login() {
    return dynamicPage(name: "prefLogIn", title: "Connect to MyQ", nextPage:"prefListDevices", uninstall:false, install: false, submitOnChange: true) {
		section("Login Credentials"){
			input("username", "email", title: "Username", description: "Email")
			input("password", "password", title: "Password", description: "Password")
		}
		section("Gateway Brand"){
			input(name: "brand", title: "Gateway Brand", type: "enum",  metadata:[values:["Liftmaster","Chamberlain","Craftsman"]] )
		}
        section("Uninstall") {
            paragraph "Tap below to completely uninstall this SmartApp and devices (doors and lamp control devices will be force-removed from automations and SmartApps)"
            href(name: "href", title: "Uninstall", required: false, page: "prefUninstall")
        }
    }
}

def prefUninstall() {
    log.debug "Removing MyQ Devices..."
    def msg = ""
    childDevices.each {
		try{
			deleteChildDevice(it.deviceNetworkId, true)
            msg = "Devices have been removed. Tap remove to complete the process."

		}
		catch (e) {
			log.debug "Error deleting ${it.deviceNetworkId}: ${e}"
            msg = "There was a problem removing your device(s). Check the IDE logs for details."
		}
	}

    return dynamicPage(name: "prefUninstall",  title: "Uninstall", install:false, uninstall:true) {
        section("Uninstallation"){
			paragraph msg
		}
    }
}


def prefListDevices() {
    //getSelectedDevices("lights")
    if (forceLogin()) {
		def doorList = getDoorList()
		if ((state.doorList) || (state.lightList)){
        	def nextPage = "prefSensor1"
            if (!state.doorList){nextPage = "summary"}  //Skip to summary if there are no doors to handle
                return dynamicPage(name: "prefListDevices",  title: "Devices", nextPage:nextPage, install:false, uninstall:true) {
                    if (state.doorList) {
                        section("Select which garage door/gate to use"){
                            input(name: "doors", type: "enum", required:false, multiple:true, metadata:[values:state.doorList])
                        }
                    }
                    if (state.lightList) {
                        section("Select which lights to use"){
                            input(name: "lights", type: "enum", required:false, multiple:true, metadata:[values:state.lightList])
                        }
                    }
                    section("Advanced (optional)", hideable: true, hidden:true){
        	            paragraph "BETA: Enable the below option if you would like to force the Garage Doors to behave as Door Locks (sensor required)." +
                        			"This may be desirable if you only want doors to open up via PIN with Alexa voice commands. " +
                                    "Note this is still considered highly experimental and may break many other automations/apps that need the garage door capability."
        	            input "prefUseLockType", "bool", required: false, title: "Create garage doors as door locks?"
					}
                }

        }else {
			def devList = getDeviceList()
			return dynamicPage(name: "prefListDevices",  title: "Error!", install:false, uninstall:true) {
				section(""){
					paragraph "Could not find any supported device(s). Please report to author about these devices: " +  devList
				}
			}
		}


	} else {
		return dynamicPage(name: "prefListDevices",  title: "Error!", install:false, uninstall:true) {
			section(""){
				paragraph "The username or password you entered is incorrect. Try again. "
			}
		}
	}
}


def summary() {
	state.installMsg = ""
    initialize()
    versionCheck()
    return dynamicPage(name: "summary",  title: "Summary", install:true, uninstall:true) {
        section("Installation Details:"){
			paragraph state.installMsg
            paragraph state.versionWarning
		}
    }
}

/* Initialization */
def installed() {
	if (door1Sensor && state.validatedDoors){
    	refreshAll()
        unschedule()
    	runEvery30Minutes(refreshAll)
    }
}

def updated() {
	log.debug "Updated..."
    if (state.previousVersion != state.thisSmartAppVersion){
    	getVersionInfo(state.previousVersion, state.thisSmartAppVersion);
    }
    if (door1Sensor && state.validatedDoors){
    	refreshAll()
        unschedule()
    	runEvery30Minutes(refreshAll)
    }
}

def uninstall(){
    log.debug "Removing MyQ Devices..."
    childDevices.each {
		try{
			deleteChildDevice(it.deviceNetworkId, true)
		}
		catch (e) {
			log.debug "Error deleting ${it.deviceNetworkId}: ${e}"
		}
	}
}

def uninstalled() {
	log.debug "MyQ removal complete."
    getVersionInfo(state.previousVersion, 0);
}

def initialize() {
	unsubscribe()
    log.debug "Initializing..."
    login()
    state.sensorMap = [:]

    // Get initial device status in state.data
	state.polling = [ last: 0, rescheduler: now() ]
	state.data = [:]

	// Create selected devices
	def doorsList = getDoorList()
	def lightsList = state.lightList

    if (doors != null){
        def firstDoor = state.validatedDoors[0]
        //Handle single door (sometimes it's just a dumb string thanks to the simulator)
        if (doors instanceof String)
        firstDoor = doors

        //Create door devices
        createChilDevices(firstDoor, door1Sensor, doorsList[firstDoor], prefDoor1PushButtons)
        if (state.validatedDoors[1]) createChilDevices(state.validatedDoors[1], door2Sensor, doorsList[state.validatedDoors[1]], prefDoor2PushButtons)
        if (state.validatedDoors[2]) createChilDevices(state.validatedDoors[2], door3Sensor, doorsList[state.validatedDoors[2]], prefDoor3PushButtons)
        if (state.validatedDoors[3]) createChilDevices(state.validatedDoors[3], door4Sensor, doorsList[state.validatedDoors[3]], prefDoor4PushButtons)
        if (state.validatedDoors[4]) createChilDevices(state.validatedDoors[4], door5Sensor, doorsList[state.validatedDoors[4]], prefDoor5PushButtons)
        if (state.validatedDoors[5]) createChilDevices(state.validatedDoors[5], door6Sensor, doorsList[state.validatedDoors[5]], prefDoor6PushButtons)
        if (state.validatedDoors[6]) createChilDevices(state.validatedDoors[6], door7Sensor, doorsList[state.validatedDoors[6]], prefDoor7PushButtons)
        if (state.validatedDoors[7]) createChilDevices(state.validatedDoors[7], door8Sensor, doorsList[state.validatedDoors[7]], prefDoor8PushButtons)
    }

    //Create light devices
    def selectedLights = getSelectedDevices("lights")
    selectedLights.each {
    	log.debug "Checking for existing light: " + it
    	if (!getChildDevice(it)) {
        	log.debug "Creating child light device: " + it

            try{
            	addChildDevice("brbeaird", "MyQ Light Controller", it, getHubID(), ["name": lightsList[it]])
                state.installMsg = state.installMsg + lightsList[it] + ": created light device. \r\n\r\n"
            }
            catch(physicalgraph.app.exception.UnknownDeviceTypeException e)
            {
                log.debug "Error! " + e
                state.installMsg = state.installMsg + lightsList[it] + ": problem creating light device. Check your IDE to make sure the brbeaird : MyQ Light Controller device handler is installed and published. \r\n\r\n"
            }
        }
        else{
        	log.debug "Light device already exists: " + it
            state.installMsg = state.installMsg + lightsList[it] + ": light device already exists. \r\n\r\n"
        }

    }

    // Remove unselected devices
    def selectedDevices = [] + getSelectedDevices("doors") + getSelectedDevices("lights")
    getChildDevices().each{
        //Modify DNI string for the extra pushbuttons to make sure they don't get deleted unintentionally
        def DNI = it?.deviceNetworkId
        DNI = DNI.replace(" Opener", "")
        DNI = DNI.replace(" Closer", "")

        if (!(DNI in selectedDevices)){
            log.debug "found device to delete: " + it
            try{
                	deleteChildDevice(it.deviceNetworkId, true)
            } catch (e){
                	sendPush("Warning: unable to delete door or button - " + it + "- you'll need to manually remove it.")
                    log.debug "Error trying to delete device " + it + " - " + e
                    log.debug "Device is likely in use in a Routine, or SmartApp (make sure and check SmarTiles!)."
            }
        }
    }

    //Create subscriptions
    if (door1Sensor)
        subscribe(door1Sensor, "contact", sensorHandler)
    if (door2Sensor)
        subscribe(door2Sensor, "contact", sensorHandler)
    if (door3Sensor)
        subscribe(door3Sensor, "contact", sensorHandler)
    if (door4Sensor)
        subscribe(door4Sensor, "contact", sensorHandler)
    if (door5Sensor)
        subscribe(door5Sensor, "contact", sensorHandler)
    if (door6Sensor)
        subscribe(door6Sensor, "contact", sensorHandler)
    if (door7Sensor)
        subscribe(door7Sensor, "contact", sensorHandler)
    if (door8Sensor)
        subscribe(door8Sensor, "contact", sensorHandler)

    if (door1Acceleration)
        subscribe(door1Acceleration, "acceleration", sensorHandler)
    if (door2Acceleration)
        subscribe(door2Acceleration, "acceleration", sensorHandler)
    if (door3Acceleration)
        subscribe(door3Acceleration, "acceleration", sensorHandler)
    if (door4Acceleration)
        subscribe(door4Acceleration, "acceleration", sensorHandler)
    if (door5Acceleration)
        subscribe(door5Acceleration, "acceleration", sensorHandler)
    if (door6Acceleration)
        subscribe(door6Acceleration, "acceleration", sensorHandler)
    if (door7Acceleration)
        subscribe(door7Acceleration, "acceleration", sensorHandler)
    if (door8Acceleration)
        subscribe(door8Acceleration, "acceleration", sensorHandler)

    //Set initial values
    if (door1Sensor && state.validatedDoors){
    	log.debug "Doing the sync"
    	syncDoorsWithSensors()
    }

    //Force a refresh sync with sensors on mode change and each day at sunrise and sunset (in cases where the devices become out of sync)
    subscribe(location, "mode", refreshAll)
    subscribe(location, "sunset", refreshAll)
    subscribe(location, "sunrise", refreshAll)
}

def getSelectedDevices( settingsName ) {
	def selectedDevices = []
	(!settings.get(settingsName))?:((settings.get(settingsName)?.getAt(0)?.size() > 1)  ? settings.get(settingsName)?.each { selectedDevices.add(it) } : selectedDevices.add(settings.get(settingsName)))
	return selectedDevices
}


/* Access Management */
private forceLogin() {
	//Reset token and expiry
	state.session = [ brandID: 0, brandName: settings.brand, securityToken: null, expiration: 0 ]
	state.polling = [ last: 0, rescheduler: now() ]
	state.data = [:]
	return doLogin()
}


private login() { return (!(state.session.expiration > now())) ? doLogin() : true }

private doLogin() {
    log.trace "Logging in"

    return apiPostLogin("/api/v4/User/Validate", [username: settings.username, password: settings.password] ) { response ->
        if (response.data.SecurityToken != null) {
            state.session.brandID = response.data.BrandId
            state.session.brandName = response.data.BrandName
            state.session.securityToken = response.data.SecurityToken
            state.session.expiration = now() + (7*24*60*60*1000) // 7 days default
            return true
        } else {
            log.warn "No security token found, login unsuccessful"
            state.session = [ brandID: 0, brandName: settings.brand, securityToken: null, expiration: 0 ] // Reset token and expiration
            return false
        }
    }
}


// Listing all the garage doors you have in MyQ
private getDoorList() {
	state.doorList = [:]
    state.lightList = [:]

    def deviceList = [:]
	apiGet(getDevicesURL(), []) { response ->
		if (response.status == 200) {
            //log.debug "response data: " + response.data
            //sendAlert("response data: " + response.data.Devices)
			response.data.Devices.each { device ->
				// 2 = garage door, 5 = gate, 7 = MyQGarage(no gateway), 9 = commercial door, 17 = Garage Door Opener WGDO
				if (device.MyQDeviceTypeId == 2||device.MyQDeviceTypeId == 5||device.MyQDeviceTypeId == 7||device.MyQDeviceTypeId == 17||device.MyQDeviceTypeId == 9) {
					log.debug "Found door: " + device.MyQDeviceId
                    def dni = [ app.id, "GarageDoorOpener", device.MyQDeviceId ].join('|')
					def description = ''
                    def doorState = ''
                    def updatedTime = ''
                    device.Attributes.each {

                        if (it.AttributeDisplayName=="desc")	//deviceList[dni] = it.Value
                        {
                        	description = it.Value
                        }

						if (it.AttributeDisplayName=="doorstate") {
                        	doorState = it.Value
                            updatedTime = it.UpdatedTime
						}
					}


                    //Sometimes MyQ has duplicates. Check and see if we've seen this door before
                        def doorToRemove = ""
                        state.data.each { doorDNI, door ->
                        	if (door.name == description){
                            	log.debug "Duplicate door detected. Checking to see if this one is newer..."

                                //If this instance is newer than the duplicate, pull the older one back out of the array
                                if (door.lastAction < updatedTime){
                                	log.debug "Yep, this one is newer."
                                    doorToRemove = door
                                }

                                //If this door is the older one, clear out the description so it will be ignored
                                else{
                                	log.debug "Nope, this one is older. Stick with what we've got."
                                    description = ""
                                }
                            }
                        }
                        if (doorToRemove){
                        	log.debug "Removing older duplicate."
                            state.data.remove(door)
                            state.doorList.remove(door)
                        }

                    //Ignore any doors with blank descriptions
                    if (description != ''){
                        log.debug "Storing door info: " + description + "type: " + device.MyQDeviceTypeId + " status: " + doorState +  " type: " + device.MyQDeviceTypeName
                        deviceList[dni] = description
                        state.doorList[dni] = description
                        state.data[dni] = [ status: doorState, lastAction: updatedTime, name: description, type: device.MyQDeviceTypeId ]
                    }
                    else{
                    	log.debug "Door " + device.MyQDeviceId + " has blank desc field. This is unusual..."
                    }
				}

                //Lights!
                if (device.MyQDeviceTypeId == 3) {
					log.debug "Found light: " + device.MyQDeviceId
                    def dni = [ app.id, "LightController", device.MyQDeviceId ].join('|')
					def description = ''
                    def lightState = ''
                    def updatedTime = ''
                    device.Attributes.each {

                        if (it.AttributeDisplayName=="desc")	//deviceList[dni] = it.Value
                        {
                        	description = it.Value
                        }

						if (it.AttributeDisplayName=="lightstate") {
                        	lightState = it.Value
                            updatedTime = it.UpdatedTime
						}
					}

                    //Ignore any lights with blank descriptions
                    if (description && description != ''){
                        log.debug "Storing light info: " + description + "type: " + device.MyQDeviceTypeId + " status: " + doorState +  " type: " + device.MyQDeviceTypeName
                        deviceList[dni] = description
                        state.lightList[dni] = description
                        state.data[dni] = [ status: lightState, lastAction: updatedTime, name: description, type: device.MyQDeviceTypeId ]
                    }
				}
			}
		}
	}
	return deviceList
}

// HTTP GET call
private apiGet(apiPath, apiQuery = [], callback = {}) {
    if (!state.session.securityToken) { // Get a token
        if (!doLogin()) {
            log.error "Unable to complete GET, login failed"
            return
        }
    }

    def myHeaders = [
        "User-Agent": "Chamberlain/3.73",
        "SecurityToken": state.session.securityToken,
        "BrandId": "2",
        "ApiVersion": "4.1",
        "Culture": "en",
        "MyQApplicationId": getApiAppID()
    ]

    try {
        httpGet([ uri: getApiURL(), path: apiPath, headers: myHeaders, query: apiQuery ]) { response ->
            log.debug "Got GET response: Retry: ${atomicState.retryCount} of ${MAX_RETRIES}\nSTATUS: ${response.status}\nHEADERS: ${response.headers?.collect { "${it.name}: ${it.value}\n" }}\nDATA: ${response.data}"
            if (response.status == 200) {
                switch (response.data.ReturnCode as Integer) {
                    case -3333: // Login again
                    	if (atomicState.retryCount <= MAX_RETRIES) {
                        	atomicState.retryCount = (atomicState.retryCount ?: 0) + 1
                            log.warn "GET: Login expired, logging in again"
                            doLogin()
                            apiGet(apiPath, apiQuery, callback) // Try again
                        } else {
                            log.warn "Too many retries, dropping request"
                        }
                        break

                    case 0: // Process response
                    	atomicState.retryCount = 0 // Reset it
                    	callback(response)
                        break

                    default:
                    	log.error "Unknown GET return code ${response.data.ReturnCode}, error ${response.data.ErrorMessage}"
                }
            } else {
                log.error "Unknown GET status: ${response.status}"
            }
        }
    }	catch (e)	{
        log.error "API GET Error: $e"
    }
}

// HTTP PUT call
private apiPut(apiPath, apiBody = [], callback = {}) {
    if (!state.session.securityToken) { // Get a token
        if (!doLogin()) {
            log.error "Unable to complete PUT, login failed"
            return
        }
    }
    def myHeaders = [
        "User-Agent": "Chamberlain/3.73",
        "SecurityToken": state.session.securityToken,
        "BrandId": "2",
        "ApiVersion": "4.1",
        "Culture": "en",
        "MyQApplicationId": getApiAppID()
    ]

    try {
        httpPut([ uri: getApiURL(), path: apiPath, headers: myHeaders, body: apiBody ]) { response ->
            log.debug "Got PUT response: Retry: ${atomicState.retryCount} of ${MAX_RETRIES}\nSTATUS: ${response.status}\nHEADERS: ${response.headers?.collect { "${it.name}: ${it.value}\n" }}\nDATA: ${response.data}"
            if (response.status == 200) {
                switch (response.data.ReturnCode as Integer) {
                    case -3333: // Login again
                    	if (atomicState.retryCount <= MAX_RETRIES) {
                        	atomicState.retryCount = (atomicState.retryCount ?: 0) + 1
                            log.warn "PUT: Login expired, logging in again"
                            doLogin()
                            apiPut(apiPath, apiBody, callback) // Try again
                        } else {
                            log.warn "Too many retries, dropping request"
                        }
                        break

                    case 0: // Process response
                    	atomicState.retryCount = 0 // Reset it
                    	callback(response)
                        break

                    default:
                    	log.error "Unknown PUT return code ${response.data.ReturnCode}, error ${response.data.ErrorMessage}"
                }
            } else {
                log.error "Unknown PUT status: ${response.status}"
            }
        }
    } catch (e)	{
        log.error "API PUT Error: $e"
    }
}


// HTTP POST call
private apiPostLogin(apiPath, apiBody = [], callback = {}) {
    def myHeaders = [
        "User-Agent": "Chamberlain/3.73",
        "BrandId": "2",
        "ApiVersion": "4.1",
        "Culture": "en",
        "MyQApplicationId": getApiAppID()
    ]

    try {
        return httpPost([ uri: getApiURL(), path: apiPath, headers: myHeaders, body: apiBody ]) { response ->
            log.debug "Got LOGIN POST response: STATUS: ${response.status}\nHEADERS: ${response.headers?.collect { "${it.name}: ${it.value}\n" }}\nDATA: ${response.data}"
            if (response.status == 200) {
                switch (response.data.ReturnCode as Integer) {
                    case 0: // Process response
                    	return callback(response)
                        break

                    default:
                    	log.error "Unknown LOGIN POST return code ${response.data.ReturnCode}, error ${response.data.ErrorMessage}"
                }
            } else {
                log.error "Unknown LOGIN POST status: ${response.status}"
            }
            
            return false
        }
    } catch (e)	{
        log.warn "API POST Error: $e"
    }
    
    return false
}


private getApiAppID() {
    return "OA9I/hgmPHFp9RYKJqCKfwnhh28uqLJzZ9KOJf1DXoo8N2XAaVX6A1wcLYyWsnnv"
}