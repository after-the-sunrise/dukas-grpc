# dukas-grpc
[![Build Status][travis-icon]][travis-page]

**dukas-grpc** is a standalone server application for providing [gRPC][grpc-home] access to [Dukascopy][dukascopy-home]'s JForex trading platform.
The official [JForex SDK][dukascopy-wiki] allows the user to use JForex API by programming custom Java applications.
**dukas-grpc** server leverages this official SDK and provides gRPC access for the SDK.

* [gRPC][grpc-home] interface for request/response communication and publish/subscribe stream messaging.
* Optimized messaging performance over HTTP/2 or unix domain sockets.
* Automatic connection management with JForex platform. (cf: connect, throttled-reconnect, ...)

## Getting Started

### Trading Account

First of all, a valid [Dukascopy][dukascopy-home] trading account is required for the API access.
If you do not have one yet, a demo account can be created on the fly.

The IP address of the **dukas-grpc** server machine needs to be registered to the account. 
This is mandatory, since API access from an unregistered IP address will need to provide a dynamic PIN code, 
which is not ideal for automated server applications.
IP whitelisting can be done via "Summary > My Account > Security Settings > IP registration" after logging into the official website. 

### Launching Server

* Java Runtime Environment 11 or later is required. (cf: `sudo dnf -y install java-11-openjdk`).
* Download the WAR archive from [releases][github-releases] page.
* Configure login credentials as either environment variables (`foo=bar java -jar ...`), or as system properties (`-Dfoo=bar`).
* List of available configuration keys and default values can be found in `com.after_sunrise.dukascopy.grpc.Config.java`.
* Use web-application container of your choice to deploy the war file, such as [jetty-runner].

After launching the application, search the log output for the following lines to confirm that the login credentials are configured correctly.

```text
yyyy-MM-dd HH:mm:ss.SSS|INFO |...|IClient connecting... (url=http://platform.dukas.../jforex.jnlp,..., user=DemoUser)
```

## Sample Client/Server Applications

* Create and configure a configuration file `./logs/dukas-test.properties` containing the login credentials.
* From an IDE, launch `com.after_sunrise.dukascopy.grpc.WebappServerTest#main` to launch the server application.
* From an IDE, launch `com.after_sunrise.dukascopy.grpc.WebappClientTest` methods to try out the requests and subscriptions.
* gRPC IDL file can be found at `./src/main/proto/dukascopy.proto`.

## Bulding from Source

JDK 11 or later is required. Make sure the `JAVA_HOME` environment variable is configured.
Then Clone the repository, and use gradle(w) to build the archives.

```shell script
$JAVA_HOME/bin/java -version

git clone git@github.com:after-the-sunrise/dukas-grpc.git

cd dukas-grpc && ./gradlew clean build
```

The archive is generated under `./build/libs/` directory.

[travis-page]:https://travis-ci.org/after-the-sunrise/dukas-grpc
[travis-icon]:https://travis-ci.org/after-the-sunrise/dukas-grpc.svg?branch=master
[github-releases]:https://github.com/after-the-sunrise/dukas-grpc/releases
[dukascopy-home]:https://www.dukascopy.com/
[dukascopy-wiki]:https://www.dukascopy.com/wiki/en/development
[grpc-home]:https://grpc.io/
[jetty-runner]:https://www.eclipse.org/jetty/documentation/current/runner.html
