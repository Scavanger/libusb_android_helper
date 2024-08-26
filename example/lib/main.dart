import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter/services.dart';
import 'package:libusb_android_helper/libusb_android_helper.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _message = "";
  UsbDevice? _device;
  
  Future<List<UsbDevice>> _getUsbDevices() async {
    List<UsbDevice>? devices = await LibusbAndroidHelper.listDevices();
    if (devices != null) {
      return devices;
    } else {
      return List<UsbDevice>.empty();
    }
  }

  Future<void> _requestPermissionAndOpenDevice() async {
    if (_device == null) {
      return;
    }
    if (!(await _device!.hasPermission())){
      await _device!.requestPermission();
    }
    await _device!.open();
  }

  String getDeviceMessage() {
    if (_device != null && _device!.isOpen && _device!.handle != 0) {
      return "Successfully opened device \"${_device!.identifier}\" with handle \"${_device!.handle}\"";
    } else {
      return "Error: Unable to open device";
    }
  }

  @override
  void initState() {
    super.initState();
    initUsbDevices();
    String msg = "";
    LibusbAndroidHelper.usbEventStream?.listen((event) async {
      try {
        if (event.action == UsbAction.usbDeviceAttached) {  
          _device = event.device;
          await _requestPermissionAndOpenDevice();
          msg = getDeviceMessage();
        } else if (event.action == UsbAction.usbDeviceDetached) {
          _device = null;
          msg = "USB device disconnected";
        }
      } on PlatformException catch (e) {
        _device = null;
        msg = "Error: ${e.message}";
      }
      if (mounted) {
        setState(() => _message = msg);
      }
    });
  }

  void initUsbDevices() async {
    String msg = "";
    try {
      List<UsbDevice> devices = await _getUsbDevices();
      if (devices.isNotEmpty) {
        _device = devices.first;
        await _requestPermissionAndOpenDevice();
        msg = getDeviceMessage();
      }
    } on PlatformException catch(e) {
      msg = "Error: ${e.message}";
    }
    if (mounted) { 
      setState(() => _message = msg);
    }    
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('libusb_android_helper example app'),
        ),
        body: Center(
          child: Text(_message.isEmpty ? "No device connected" : _message),
        ),
      ),
    );
  }
}
