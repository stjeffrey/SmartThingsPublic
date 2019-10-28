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

import groovy.transform.Field
@Field final MAX_RETRIES = 2 // Retry count before giving up

preferences {
    page(name: "preferences_Login", title: "MyQ")
	page(name: "prefListDevices", title: "MyQ")
    page(name: "prefSensor1", title: "MyQ")
    page(name: "noDoorsSelected", title: "MyQ")
    page(name: "summary", title: "MyQ")
    page(name: "prefUninstall", title: "MyQ")
}

/* Preferences */
def preferences_Login() {
    return dynamicPage(name: "preferences_Login", title: "Connect to MyQ", nextPage:"prefListDevices", uninstall:false, install: false, submitOnChange: true) {
		section("Login Credentials"){
			input("username", "email", title: "Username", description: "Email")
			input("password", "password", title: "Password", description: "Password")
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
    if (forceLogin()) {
		def doorList = getDoorList()
		if ((state.doorList)){
        	log.debug "found doors"
        	def nextPage = "prefSensor1"
            if (!state.doorList) {
            	nextPage = "summary"
            }  //Skip to summary if there are no doors to handle
                
            return dynamicPage(name: "prefListDevices",  title: "Devices", nextPage:nextPage, install:false, uninstall:true) {
                if (state.doorList) {
                    section("Select which garage door/gate to use"){
                        input(name: "doors", type: "enum", required:false, multiple:true, metadata:[values:state.doorList])
                    }
                }
            }
        } else {
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

def prefSensor1() {
    log.debug "Doors chosen: " + doors

    //Sometimes ST has an issue where stale options are not properly dropped from settings. Let's get a true count of valid doors selected
    state.validatedDoors = []
    if (doors instanceof List && doors.size() > 1){
        doors.each {
            if (state.data[it] != null){
                state.validatedDoors.add(it)
            }
        }
    }
    else{
    	state.validatedDoors = doors	//Handle single door
    }

    log.debug "Valid doors chosen: " + state.validatedDoors

    //Set defaults
    def nextPage = "summary"
    def titleText = ""

    //If no doors chosen, skip to summary
    if (!state.validatedDoors){
        return dynamicPage(name: "noDoorsSelected",  title: "No doors selected. Tap next to finish.", nextPage:nextPage, install:false, uninstall:true) {
            section(titleText){
                paragraph "No doors selected. Tap next to finish"
            }
        }
    }

    //Determine if we have multiple doors and need to send to another page
    if (doors instanceof String){ //simulator seems to just make a single door a string. For that reason we have this weird check.
        log.debug "Single door detected (string)."
        titleText = "Select Sensors for Door 1 (" + state.data[doors].name + ")"
    }
    else if (doors.size() == 1){
        log.debug "Single door detected (array)."
        titleText = "Select Sensors for Door 1 (" + state.data[doors[0]].name + ")"
    }
    else{
        log.debug "Multiple doors detected."
        log.debug state.validatedDoors[0]
        nextPage = "prefSensor2"
        titleText = "OPTIONAL: Select Sensors for Door 1 (" + state.data[state.validatedDoors[0]].name + ")"
    }

    return dynamicPage(name: "prefSensor1",  title: "Push Buttons", nextPage:nextPage, install:false, uninstall:true) {
        section("Create separate on/off push buttons?"){
			paragraph "Choose the option below to have separate additional On and Off push button devices created. This is recommened if you have no sensors but still want a way to open/close the " +
            "garage from SmartTiles and other interfaces like Google Home that can't function with the built-in open/close capability. See wiki for more details."
            input "prefDoor1PushButtons", "bool", required: false, title: "Create on/off push buttons?"
		}
    }
}

def getHubID(){
    def hubs = location.hubs.findAll{ it.type == physicalgraph.device.HubType.PHYSICAL }
    log.debug "Found ${hubs.size()} hub(s) at this location."

    //Try and find a valid hub on the account
    def chosenHub
    hubs.each {
        if (it != null){
        	log.debug "Valid hub found: ${it} (${it.id})"
            chosenHub = it
        }
    }

    if (chosenHub != null){
        log.debug "Chosen hub for child devices: ${chosenHub} (${chosenHub.id})"
        return chosenHub.id
    }
    else{
        log.debug "No physical hubs found. Sending NULL"
        return null
    }
}

def summary() {
	log.debug "running summary"
	state.installMsg = ""
    initialize()
    return dynamicPage(name: "summary",  title: "Summary", install:true, uninstall:true) {
        section("Installation Details:"){
			paragraph state.installMsg
            //paragraph state.versionWarning
		}
    }
}

/* Initialization */
def installed() {
	log.debug "running installed"
	if (door1Sensor && state.validatedDoors){
    	refreshAll()
        unschedule()
    	runEvery5Minutes(refreshAll)
    }
}

def updated() {
	log.debug "Updated..."
    if (door1Sensor && state.validatedDoors){
    	refreshAll()
        unschedule()
    	runEvery5Minutes(refreshAll)
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
}

def initialize() {
	unsubscribe()
    log.debug "Initializing..."
    login()

    // Get initial device status in state.data
	state.polling = [ last: 0, rescheduler: now() ]
	state.data = [:]

	// Create selected devices
	def doorsList = getDoorList()
	
    if (doors != null){
        def firstDoor = state.validatedDoors[0]
        //Handle single door (sometimes it's just a dumb string thanks to the simulator)
        if (doors instanceof String)
        firstDoor = doors

		log.debug "firstDoor is: " + firstDoor
        log.debug "door list: " + doorsList
        
        //Create door devices
        createChilDevices(firstDoor, door1Sensor, doorsList[firstDoor], prefDoor1PushButtons)
    }

    // Remove unselected devices
    def selectedDevices = [] + getSelectedDevices("doors")
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


def createChilDevices(door, sensor, doorName, prefPushButtons){
	log.debug "In CreateChild"
    def sensorTypeName = "MyQ Garage Door Opener"

    if (door){
        //Has door's child device already been created?
        def existingDev = getChildDevice(door)
        def existingType = existingDev?.typeName

        if (existingDev){
        	log.debug "Child already exists for " + doorName + ". Sensor name is: " + sensor
            state.installMsg = state.installMsg + doorName + ": door device already exists. \r\n\r\n"

            if (sensor && existingType != sensorTypeName){
            	try{
                    log.debug "Type needs updating to sensor version"
                    existingDev.deviceType = sensorTypeName
                    state.installMsg = state.installMsg + doorName + ": changed door device to sensor version." + "\r\n\r\n"
                }
                catch(physicalgraph.exception.NotFoundException e)
                {
                    log.debug "Error! " + e
                    state.installMsg = state.installMsg + doorName + ": problem changing door to sensor type. Check your IDE to make sure the brbeaird : " + sensorTypeName + " device handler is installed and published. \r\n\r\n"
                }
            }
        }
        else{
            log.debug "Creating child door device " + door

                try{
                    log.debug "Creating door with sensor"
                    addChildDevice("stjeffrey", sensorTypeName, door, getHubID(), ["name": doorName])
                    state.installMsg = state.installMsg + doorName + ": created door device (sensor version) \r\n\r\n"
                }
                catch(physicalgraph.app.exception.UnknownDeviceTypeException e)
                {
                    log.debug "Error! " + e
                    state.installMsg = state.installMsg + doorName + ": problem creating door device (sensor type). Check your IDE to make sure the brbeaird : " + sensorTypeName + " device handler is installed and published. \r\n\r\n"

                }
        }

        //Create push button devices
        if (prefPushButtons){
        	def existingOpenButtonDev = getChildDevice(door + " Opener")
            def existingCloseButtonDev = getChildDevice(door + " Closer")
            if (!existingOpenButtonDev){
                try{
                	def openButton = addChildDevice("smartthings", "Momentary Button Tile", door + " Opener", getHubID(), [name: doorName + " Opener", label: doorName + " Opener"])
                	state.installMsg = state.installMsg + doorName + ": created push button device. \r\n\r\n"
                	subscribe(openButton, "momentary.pushed", doorButtonOpenHandler)
                }
                catch(physicalgraph.app.exception.UnknownDeviceTypeException e)
                {
                    log.debug "Error! " + e
                    state.installMsg = state.installMsg + doorName + ": problem creating push button device. Check your IDE to make sure the smartthings : Momentary Button Tile device handler is installed and published. \r\n\r\n"
                }
            }
            else{
            	subscribe(existingOpenButtonDev, "momentary.pushed", doorButtonOpenHandler)
                state.installMsg = state.installMsg + doorName + ": push button device already exists. Subscription recreated. \r\n\r\n"
                log.debug "subscribed to button: " + existingOpenButtonDev
            }

            if (!existingCloseButtonDev){
                try{
                    def closeButton = addChildDevice("smartthings", "Momentary Button Tile", door + " Closer", getHubID(), [name: doorName + " Closer", label: doorName + " Closer"])
                    subscribe(closeButton, "momentary.pushed", doorButtonCloseHandler)
                }
                catch(physicalgraph.app.exception.UnknownDeviceTypeException e)
                {
                    log.debug "Error! " + e
                }
            }
            else{
                subscribe(existingCloseButtonDev, "momentary.pushed", doorButtonCloseHandler)
            }
        }

        //Cleanup defunct push button devices if no longer wanted
        else{
        	def pushButtonIDs = [door + " Opener", door + " Closer"]
            log.debug "ID's to look for: " + pushButtonIDs
            def devsToDelete = getChildDevices().findAll { pushButtonIDs.contains(it.deviceNetworkId)}
            log.debug "button devices to delete: " + devsToDelete
			devsToDelete.each{
            	log.debug "deleting button: " + it
                try{
                	deleteChildDevice(it.deviceNetworkId, true)
                } catch (e){
                	//sendPush("Warning: unable to delete virtual on/off push button - you'll need to manually remove it.")
                    state.installMsg = state.installMsg + "Warning: unable to delete virtual on/off push button - you'll need to manually remove it. \r\n\r\n"
                    log.debug "Error trying to delete button " + it + " - " + e
                    log.debug "Button  is likely in use in a Routine, or SmartApp (make sure and check SmarTiles!)."
                }

            }
        }
    }
}

/* Access Management */
private forceLogin() {
	//Reset token and expiry
	state.session = [ securityToken: null, expiration: 0 ]
	state.polling = [ last: 0, rescheduler: now() ]
	state.data = [:]
	return doLogin()
}

private login() { 
    log.debug "expiration is :" + state.session.expiration
    return (!(state.session.expiration > now())) ? doLogin() : true 
}

private doLogin() {
    log.trace "Logging in"

    return apiPostLogin("/api/v5/Login", [username: settings.username, password: settings.password] ) { response ->
        if (response.data.SecurityToken != null) {
            state.session.securityToken = response.data.SecurityToken
            state.session.expiration = now() + (7*24*60*60*1000) // 7 days default
            return true
        } else {
            log.warn "No security token found, login unsuccessful"
            state.session = [ securityToken: null, expiration: 0 ] // Reset token and expiration
            return false
        }
    }
}

def syncDoorsWithSensors(child){
    def firstDoor = state.validatedDoors[0]

    //Handle single door (sometimes it's just a dumb string thanks to the simulator)
    if (doors instanceof String)
        firstDoor = doors

    def doorDNI = null
    if (child) {								// refresh only the requesting door (makes things a bit more efficient if you have more than 1 door
    	doorDNI = child.device.deviceNetworkId
        switch (doorDNI) {
        	case firstDoor:
            	updateDoorStatus(firstDoor, door1Sensor, door1Acceleration, door1ThreeAxis, child)
                break
     	}
    } else {    					// refresh ALL the doors
		if (firstDoor) updateDoorStatus(firstDoor, door1Sensor, door1Acceleration, door1ThreeAxis, null)
    }
}

def updateDoorStatus(doorDNI, sensor, acceleration, threeAxis, child){
    try {
        log.debug "trying to refresh door dni: " + doorDNI
        //Get door to update and set the new value
        def doorToUpdate = getChildDevice(doorDNI)
        def doorName = "unknown"
        if (state.data[doorDNI]){
            doorName = state.data[doorDNI].name
        }

        def value = "unknown"
        def moving = "unknown"
        def door = doorToUpdate.latestValue("door")

        if (sensor) value = sensor.latestValue("contact")
        log.debug "door: " + door + " value: " + value + " moving: " + moving 

        if (moving == "active") {
            if (value == "open") {
                if (door != "opening") value = "closing" else value = "opening"  // if door is "open" or "waiting" change to "closing", else it must be "opening"
            } else if (value == "closed") {
                if (door != "closing") 	value = "opening" else value = "closed"
            }
        } else if (moving == "inactive") {
            if (door == "closing") {
                if (value == "open") { 	// just stopped but door is still open
                    value = "stopped"
                }
            }
        }

        log.debug "doing update device status"
        doorToUpdate.updateDeviceStatus(value)
        doorToUpdate.updateDeviceSensor("${sensor} is ${sensor?.currentContact}")

        log.debug "Door: " + doorName + ": Updating with status - " + value + " -  from sensor " + sensor

        //Write to child log if this was initiated from one of the doors
        if (child)
            child.log("Door: " + doorName + ": Updating with status - " + value + " -  from sensor " + sensor)

        //Get latest activity timestamp for the sensor (data saved for up to a week)
        def eventsSinceYesterday = sensor.eventsSince(new Date() - 7)
        def latestEvent = eventsSinceYesterday[0]?.date
        def timeStampLogText = "Door: " + doorName + ": Updating timestamp to: " + latestEvent + " -  from sensor " + sensor

        if (!latestEvent)	//If the door has been inactive for more than a week, timestamp data will be null. Keep current value in that case.
            timeStampLogText = "Door: " + doorName + ": Null timestamp detected "  + " -  from sensor " + sensor + " . Keeping current value."
        else
            doorToUpdate.updateDeviceLastActivity(latestEvent)

        log.debug timeStampLogText

        //Write to child log if this was initiated from one of the doors
        if (child)
            child.log(timeStampLogText)

    }catch (e) {
        log.debug "Error updating door: ${doorDNI}: ${e}"
    }
}

def refresh(child){
    child.log("refresh called for " + child.device.deviceNetworkId)

    def url = getDeviceURL(child.device.deviceNetworkId)
    apiGet(url, []) { response ->
        child.log('got a response');
        if (response.status == 200) {
            def status = response.data.state.door_state
            def lastEvent = response.data.state.last_update
            
            lastEvent = Date.parse("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", lastEvent)
            child.log "status is: " + status
            child.log "last event is: " + lastEvent
            if(status != state.door) {
                state.lastStatus = status
                child.updateDeviceStatus(status)
                child.updateDeviceLastActivity(lastEvent)
            }
        } else {
            device.log.debug response.status
            callback(-1)
        }
    }
}

def refreshAll(){
    log.debug "refresh all"
    def devices = getRefreshList()
    getChildDevices().each{
        refresh(it)
    }
}

def refreshAll(evt){
	refreshAll()
}

def doorButtonOpenHandler(evt) {
    log.debug "Door open button push detected: Event name  " + evt.name + " value: " + evt.value   + " deviceID: " + evt.deviceId + " DNI: " + evt.getDevice().deviceNetworkId
    def doorDeviceDNI = evt.getDevice().deviceNetworkId
    doorDeviceDNI = doorDeviceDNI.replace(" Opener", "")
    def doorDevice = getChildDevice(doorDeviceDNI)
    log.debug "Opening door '${doorDeviceDNI}'"
    doorDevice.openPrep()
    sendCommand(doorDevice, "open")
}

def doorButtonCloseHandler(evt) {
    log.debug "Door close button push detected: Event name  " + evt.name + " value: " + evt.value   + " deviceID: " + evt.deviceId + " DNI: " + evt.getDevice().deviceNetworkId
    def doorDeviceDNI = evt.getDevice().deviceNetworkId
    doorDeviceDNI = doorDeviceDNI.replace(" Closer", "")
	def doorDevice = getChildDevice(doorDeviceDNI)
    log.debug "Closing door."
    doorDevice.closePrep()
    sendCommand(doorDevice, "close")
}

// Listing all the garage doors you have in MyQ
private getDoorList() {
	state.doorList = [:]

    def deviceList = [:]
	apiGet(getDevicesURL(), []) { response ->
		if (response.status == 200) {
			response.data.items.each { device ->
				if(device.device_family == 'garagedoor') {
                    log.debug "found door: ${device.serial_number}"
                    deviceList[device.serial_number] = device.name
                    state.doorList[device.serial_number] = device.name
                    state.data[device.serial_number] = [ status: device.state.door_state, lastAction: device.state.last_update, name: device.name ]
                }
			}

            return deviceList
		}
	}
}

// HTTP GET call
private apiGet(apiPath, apiQuery = [], callback = {}) {
    log.debug('making apiget call')
    if (!state.session.securityToken) { // Get a token
        log.debug "need to log in before making api get call"
        if (!doLogin()) {
            log.error "Unable to complete GET, login failed"
            return
        }
    }
    
    log.debug "securityToken = " + state.session.securityToken

    def myHeaders = [
        "User-Agent": "Chamberlain/10482 CFNetwork/978.0.7 Darwin/18.6.0",
        "SecurityToken": state.session.securityToken,
        "MyQApplicationId": getApiAppID()
    ]

	try {
        httpGet([ uri: getApiURL(), path: apiPath, headers: myHeaders, query: apiQuery ]) { response ->
            //log.debug "Got GET response: Retry: ${atomicState.retryCount} of ${MAX_RETRIES}\nSTATUS: ${response.status}\nHEADERS: ${response.headers?.collect { "${it.name}: ${it.value}\n" }}\nDATA: ${response.data}"
            if (response.status == 200) {
                callback(response)
            } else {
                log.error "Unknown GET status: ${response.status}"
            }
        }
    } catch (groovyx.net.http.HttpResponseException httpException) {
        if(httpException.getStatusCode() == 401) {
            log.debug('session expired, lets try again')
            state.session.securityToken = ""
            apiGet(apiPath, apiQuery, callback) 
        }
    } 
    catch (e)	{
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
        "User-Agent": "Chamberlain/10482 CFNetwork/978.0.7 Darwin/18.6.0",
        "SecurityToken": state.session.securityToken,
        "MyQApplicationId": getApiAppID()
    ]

    try {
        httpPut([ uri: getApiURL(), path: apiPath, headers: myHeaders, body: apiBody ]) { response ->
            log.debug "Got PUT response: Retry: ${atomicState.retryCount} of ${MAX_RETRIES}\nSTATUS: ${response.status}\nHEADERS: ${response.headers?.collect { "${it.name}: ${it.value}\n" }}\nDATA: ${response.data}"
            if (response.status == 204) {
                callback(response)
                /*
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

                    case 204: // Process response
                    	atomicState.retryCount = 0 // Reset it
                    	callback(response)
                        break

                    default:
                    	log.error "Unknown PUT return code ${response.data.ReturnCode}, error ${response.data.ErrorMessage}"
                }
                */
            } else {
                log.error "Unknown PUT status: ${response.status}"
            }
        }
    } catch (e)	{
        log.error "API PUT Error: $e"
    }
}

private getDeviceList() {
	def deviceList = []
    log.debug "getDeviceList"
	apiGet(getDevicesURL(), []) { response ->
		if (response.status == 200) {
			response.data.Devices.each { device ->
				log.debug "MyQDeviceTypeId : " + device.MyQDeviceTypeId.toString()
				if (!(device.MyQDeviceTypeId == 1||device.MyQDeviceTypeId == 2||device.MyQDeviceTypeId == 3||device.MyQDeviceTypeId == 5||device.MyQDeviceTypeId == 7)) {
                    device.Attributes.each {
						def description = ''
                        def updatedTime = ''
                        if (it.AttributeDisplayName=="desc")	//deviceList[dni] = it.Value
                        	description = it.Value

                        //Ignore any doors with blank descriptions
                        if (description && description != ''){
                        	log.debug "found device: " + description
                        	deviceList.add( device.MyQDeviceTypeId.toString() + "|" + device.TypeID )
                        }
					}
				}
			}
		}
	}
	return deviceList
}

private refreshItem(device, id, callback = {}) {
    device.log.debug("getRefreshList, id: " + id)
    def url = getDeviceURL(id)
    apiGet(url, []) { response ->
        device.log.debug('got a response');
        device.log.debug(response.data)
        if (response.status == 200) {
            device.log.debug "status is: ${response.data.state.door_state}"
            callback(response.data.state.door_state + "|" + response.data.state.last_update)
        } else {
            device.log.debug response.status
            callback(-1)
        }
    }
}

private getApiURL() {
	return "https://api.myqdevice.com"
}

private getDevicesURL() {
	return "/api/v5/Accounts/b20c88a6-7ac9-484b-b32e-706e577bea30/Devices"
}

private getDeviceURL(deviceId) {
	return "/api/v5/Accounts/b20c88a6-7ac9-484b-b32e-706e577bea30/devices/" + deviceId
}

private getDeviceActionURL(deviceId) {
    return "/api/v5/accounts/b20c88a6-7ac9-484b-b32e-706e577bea30/devices/${deviceId}/actions"
}

// HTTP POST call
private apiPostLogin(apiPath, apiBody = [], callback = {}) {
    def myHeaders = [
        "User-Agent": "Chamberlain/10482 CFNetwork/978.0.7 Darwin/18.6.0",
        "MyQApplicationId": getApiAppID()
    ]

    try {
        return httpPost([ uri: getApiURL(), path: apiPath, headers: myHeaders, body: apiBody ]) { response ->
            if (response.status == 200) {
                    return callback(response)
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

// Send command to start or stop
def sendCommand(child, desiredState) {
    state.lastCommandSent = now()
    if (login()) {
		//Send command
        child.log("calling api to ${desiredState} door")
		apiPut(getDeviceActionURL(child.device.deviceNetworkId), [action_type: desiredState]) { response ->
            if (attributeValue == 'close') {		// if we are closing, check if we have an Acceleration sensor, if so, "waiting" until it moves
                def firstDoor = state.validatedDoors[0]
                if (doors instanceof String) firstDoor = doors
                def doorDNI = child.device.deviceNetworkId
                switch (doorDNI) {
                    case firstDoor:
                        if (door1Sensor){if (door1Acceleration) child.updateDeviceStatus("waiting") else child.updateDeviceStatus("closing")}
                        break
                }
            }

            return true
        }
	}
}

// Get Device ID
def getChildDeviceID(child) {
	return child.device.deviceNetworkId
}

// Get single device status
def getDeviceStatus(child) {
	return state.data[child.device.deviceNetworkId].status
}

// Get single device last activity
def getDeviceLastActivity(child) {
	return state.data[child.device.deviceNetworkId].lastAction.toLong()
}

def notify(message){
	sendNotificationEvent(message)
}

private getApiAppID() {
    return "JVM/G9Nwih5BwKgNCjLxiFUQxQijAebyyg8QUHr7JOrP+tuPb8iHfRHKwTmDzHOu"
}