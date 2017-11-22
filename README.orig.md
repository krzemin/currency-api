# Flow Currency Rates

The Flow Commerce platform needs to consume and make available
currency exchange rates, for both internal and external use.

There are two specific use cases we need to support. Implement these
use cases using your Scala framework of choice and [Currency rates from
Fixer.io](http://fixer.io/). If you decide to use Play, you can clone a Play 2.5 application template [here](https://github.com/flowcommerce/play-scala-2.5).

## Use case 1: REST API

Implement a REST API to access currency information, providing at a
minimum:

  - A user should be able to:

        curl localhost:9000/rates?base=USD

    returns a list of all of the latest currency rates from the USD to
    all other currencies.

  - A user should be able to:

        curl localhost:9000/rates?base=USD&target=CAN

    return a list of the latest current rate from USD to CAN

  - A user should be able to provide a timestamp which would
    return the currency rate at that moment in time.

        curl localhost:9000/rates?base=USD&timestamp=2016-05-01T14:34:46Z

    returns a list of all of the currency rates from the USD to all
    other currencies at this time (May 1, 2016 at 14:34:46 UTC in this
    example)


## Use case 2: Publishing

We know that currency rates fluctuate continuously. A key feature of
the system is to enable our clients to register a webhook to receive
notifications in real time whenever a currency rate changes.

Implement the publishing system so that whenever a currency rate is
updated, an HTTP POST to the webhook URL is triggered. For the
purposes of this exercise, we will assume the webhook URL is always
http://localhost:7091/webhooks.
