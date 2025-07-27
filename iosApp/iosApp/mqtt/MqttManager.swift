import Foundation
import UIKit

@objc public class MqttManager: NSObject {

    @objc public func connect() {
        print("[Swift] MqttManager: connect() called")
        // Di sini kamu bisa hubungkan ke socket atau backend MQTT native
    }

    @objc public func publish(message: String, topic: String) {
        print("[Swift] MqttManager: publish to \(topic): \(message)")
        // Implementasi publish MQTT di sini (misal URLSession atau custom)
    }

    @objc public func disconnect() {
        print("[Swift] MqttManager: disconnect() called")
        // Putuskan koneksi
    }
}

