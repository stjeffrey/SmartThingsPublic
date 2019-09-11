metadata {
	definition (name: "MyQ Garage Door Opener", namespace: "stjeffrey", author: "Stephane Jeffrey", vid: "generic-contact-4", ocfdevicetype: "oic.d.garagedoor", mnmn: "SmartThings") {
		capability "Door Control"
		capability "Garage Door Control"
		capability "Contact Sensor"
		capability "Refresh"
		capability "Polling"

		capability "Actuator"
		capability "Momentary"
		capability "Sensor"
        //capability "Health Check" Will be needed eventually for new app compatability but is not documented well enough yet
		
		attribute "lastActivity", "string"
        attribute "doorSensor", "string"
        attribute "doorMoving", "string"
        attribute "OpenButton", "string"
        attribute "CloseButton", "string"
        
		command "updateDeviceStatus", ["string"]
		command "updateDeviceLastActivity", ["number"]
        command "updateDeviceMoving", ["string"]        
	}

	simulator {	}

	tiles {
		
		multiAttributeTile(name:"door", type: "lighting", width: 6, height: 4, canChangeIcon: false) {
			tileAttribute ("device.door", key: "PRIMARY_CONTROL") {
				attributeState "unknown", label:'${name}', icon:"st.doors.garage.garage-closed",    backgroundColor:"#ffa81e"
				attributeState "closed",  label:'${name}', action:"push",   icon:"st.doors.garage.garage-closed",  backgroundColor:"#00a0dc", nextState: "opening"
				attributeState "open",    label:'${name}', action:"push",  icon:"st.doors.garage.garage-open",    backgroundColor:"#e86d13", nextState: "closing"
				attributeState "opening", label:'${name}', action:"push",	 icon:"st.doors.garage.garage-opening", backgroundColor:"#cec236"
				attributeState "closing", label:'${name}', action:"push",	 icon:"st.doors.garage.garage-closing", backgroundColor:"#cec236"
			}
            tileAttribute("device.lastActivity", key: "SECONDARY_CONTROL") {
        		attributeState("lastActivity", label:'Last Activity: ${currentValue}', defaultState: true)
    		}
		}

		standardTile("refresh", "device.door", width: 3, height: 2, decoration: "flat") {
			state("default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh")
		}
        standardTile("openBtn", "device.OpenButton", width: 3, height: 3) {
            state "normal", label: 'Open', icon: "st.doors.garage.garage-open", backgroundColor: "#e86d13", action: "open", nextState: "opening"
            state "opening", label: 'Opening', icon: "st.doors.garage.garage-opening", backgroundColor: "#cec236", action: "open"
		}
        standardTile("closeBtn", "device.CloseButton", width: 3, height: 3) {            
            state "normal", label: 'Close', icon: "st.doors.garage.garage-closed", backgroundColor: "#00a0dc", action: "close", nextState: "closing"
            state "closing", label: 'Closing', icon: "st.doors.garage.garage-closing", backgroundColor: "#cec236", action: "close"
		}
		valueTile("doorMoving", "device.doorMoving", width: 6, height: 2, inactiveLavel: false, decoration: "flat") {
			state "default", label: '${currentValue}', backgroundColor:"#ffffff"
		}        
        main "door"
		details(["door", "openBtn", "closeBtn", "refresh"])
	}
}

def push() {	
    def doorState = device.currentState("door")?.value
	if (doorState == "open" || doorState == "stopped") {
		close()
	} else if (doorState == "closed") {
		open()
	} 
	sendEvent(name: "momentary", value: "pushed", display: false, displayed: false, isStateChange: true)
}

def open()  { 
	log.debug "Garage door open command called."
    parent.notify("Garage door open command called.")
    updateDeviceStatus("opening")
    parent.sendCommand(this, "desireddoorstate", 1) 
	
    runIn(20, refresh, [overwrite: true])	//Force a sync with tilt sensor after 20 seconds
}
def close() { 
	log.debug "Garage door close command called."
    parent.notify("Garage door close command called.")
	parent.sendCommand(this, "desireddoorstate", 0) 
//	updateDeviceStatus("closing")			// Now handled in the parent (in case we have an Acceleration sensor, we can handle "waiting" state)
    runIn(30, refresh, [overwrite: true]) //Force a sync with tilt sensor after 30 seconds
}

def refresh() {	    
    parent.refresh(this)
    sendEvent(name: "OpenButton", value: "normal", displayed: false, isStateChange: true)
    sendEvent(name: "CloseButton", value: "normal", displayed: false, isStateChange: true)
}

def poll() { refresh() }

// update status
def updateDeviceStatus(status) {	
    
    def currentState = device.currentState("door")?.value
    
    log.debug "Request received to update door status to : " + status    
    
    //Don't do anything if nothing changed
    if (currentState == status){
    	log.debug "No change; door is already set to " + status
        status = ""
    }
    
    switch (status) {
		case "open":
    		log.debug "Door is now open"
			sendEvent(name: "door", value: "open", display: true, isStateChange: true, descriptionText: device.displayName + " is open") 
            break
            
        case "closed":
			log.debug "Door is now closed"
        	sendEvent(name: "door", value: "closed", display: true, isStateChange: true, descriptionText: device.displayName + " is closed")
            break
            
		case "opening":
			if (currentState == "open"){
        		log.debug "Door is already open. Leaving status alone."
        	}
        	else{
        		sendEvent(name: "door", value: "opening", descriptionText: "Sent opening command.", display: false, displayed: true, isStateChange: true)
        	}
            break

		case "closing":
    		if(currentState == "closed"){
        		log.debug "Door is already closed. Leaving status alone."
        	}
			else{
        		sendEvent(name: "door", value: "closing", display: false, displayed: false, isStateChange: true)
        	}
            break
	
    	case "stopped":
    		if (currentState != "closed") {
    			log.debug "Door is stopped"
    			sendEvent(name: "door", value: "stopped", display: false, displayed: false, isStateChange: true)
        	}
            break
            
        case "waiting":
        	if (currentState == "open") {
            	log.debug "Door is waiting before closing"
                sendEvent(name: "door", value: "waiting", display: false, displayed: false, isStateChange: true)
            }
            break
        }
}

def updateDeviceLastActivity(lastActivity) {
	def finalString = lastActivity?.format('MM/d/yyyy hh:mm a',location.timeZone)    
	sendEvent(name: "lastActivity", value: finalString, display: false , displayed: false)
}

def updateDeviceMoving(moving) {	
	sendEvent(name: "doorMoving", value: moving, display: false , displayed: false)
}

def showVersion(){
	return "1.0.0"
}

def log(msg){
	log.debug msg
}
