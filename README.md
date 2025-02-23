![image](https://github.com/user-attachments/assets/7c08e053-c84f-41f8-bc97-f55130100419)



# Microbot
Microbot es un cliente de Runescape de código abierto basado en Runelite. Utiliza un sistema de complementos para habilitar la creación de scripts. Aquí hay un canal de YouTube que muestra algunos de los scripts.

## Youtube

[![image](https://github.com/user-attachments/assets/f15ec853-9b92-474e-a269-9a984e8bb792)](https://www.youtube.com/channel/UCEj_7N5OPJkdDi0VTMOJOpw)

## Discord

[![Discord Banner 1](https://discord.com/api/guilds/1087718903985221642/widget.png?style=banner1)](https://discord.gg/zaGrfqFEWE)

 
Si tienes alguna pregunta, únete a nuestro [Discord](https://discord.gg/zaGrfqFEWE) servidor.


## ☕ Invítame a un café

Si te gusta mi trabajo de código abierto y te gustaría apoyarme, ¡considera invitarme a un café! Tu apoyo me ayuda a mantenerme con energía y motivado para seguir mejorando y creando proyectos increíbles.

[![Invítame a un café](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-donate-yellow)](https://www.paypal.com/paypalme/MicrobotBE?country.x=BE)


![image](https://github.com/user-attachments/assets/c510631d-5ecf-4968-a916-2942f9b754f8)


BTC Address: bc1q4c63nc5jt9wem87cy7llsk2ur5psjnqhltt2kf

LTC Address: ltc1qgk0dkchfd8tf7jvtj5708vheq82k2wyqucrqs7

ETC Address: 0xf8A6d6Fae32319A93341aE45F1ED87DA2Aa04132

DOGE Address: DNHQDHKn7MKdMQRZyoSrJ68Lnd1D9bjbTn


¡Gracias por su apoyo! 😊

# Quiero jugar

## Cuenta que no es de Jagex

Aquí hay un video de YouTube sobre cómo configurar Microbot desde cero para **CUENTAS QUE NO SON DE JAGEX**

https://www.youtube.com/watch?v=EbtdZnxq5iw

## Cuenta Jagex

Siga la wiki de Runelite para configurar cuentas de Jagex: https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts

Una vez que hayas terminado de configurarlo, sigue estos dos pasos:

1) Simplemente inicie sesión con el lanzador de jagex por primera vez. Esto creará un token para su cuenta. Cierre todo después de iniciar sesión con éxito a través del lanzador de jagex.
2) Abra el archivo microbot.jar desde microbot y esto debería indicarle la cuenta de jagex para iniciar sesión.

# Quiero desarrollarme

## ¿Primera vez corriendo el proyecto como desarrollador?

Asegúrate de seguir esta guía si es la primera vez que ejecutas el proyecto.

[https://github.com/runelite/runelite/wiki/Building-with-IntelliJ-IDEA](https://github.com/chsami/microbot/wiki/Building-with-IntelliJ-IDEA)

## Microbot ChatGPT Chatbot

[![image](https://github.com/user-attachments/assets/92adb50f-1500-44c0-a069-ff976cccd317)](https://chatgpt.com/g/g-LM0fGeeXB-microbot-documentation)

Utilice este chatbot de IA para aprender a escribir scripts en [Microbot GPT](https://chatgpt.com/g/g-LM0fGeeXB-microbot-documentation)

## Diseño del proyecto

Debajo del complemento Microbot encontrarás una carpeta de utilidades que contiene todas las clases de utilidades que facilitan la interacción con el juego.

Las clases de utilidad tienen el prefijo Rs2. Por lo tanto, para el jugador es Rs2Player. Para los NPC es Rs2Npc y así sucesivamente...

Si no puedes encontrar algo específico en una clase de utilidad, siempre puedes llamar al objeto Microbot que tiene acceso a todos los objetos que expone Runelite. Entonces, para obtener la ubicación de un jugador, puedes hacer lo siguiente:

```java 
Microbot.getClient().getLocalPlayer().getWorldLocation()
```

![img.png](img.png)

## ExampleScript

Hay un script de ejemplo que puedes usar para jugar con la API.

![img_1.png](img_1.png)

¿Cómo se ve el script de ejemplo?

```java
public class ExampleScript extends Script {
public static double version = 1.0;

    public boolean run(ExampleConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            try {
                /*
                 * Important classes:
                 * Inventory
                 * Rs2GameObject
                 * Rs2GroundObject
                 * Rs2NPC
                 * Rs2Bank
                 * etc...
                 */

                long startTime = System.currentTimeMillis();
                
                //TU CÓDIGO VIENE AQUÍ
                Rs2Npc.attack("guard");
                
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 2000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
```

Todos nuestros scripts tienen una configuración. Esta es la configuración para un script específico.
Superposición, esta es una superposición visual para un script específico.
Plugin que maneja el código para iniciar y detener el script.
Script que maneja todo el código que microbot tiene que ejecutar.

Dentro del inicio de un plugin podemos llamar al código del script de esta manera:

```java
@Override
protected void startUp() throws AWTException {
if (overlayManager != null) {
overlayManager.add(exampleOverlay);
}
//Llama a tu SCRIPT.RUN
exampleScript.run(config);
}
```

Créditos a runelite por hacer todo esto posible. <3

https://github.com/runelite/runelite

### Licencia

RuneLite tiene licencia BSD de 2 cláusulas. Consulta el encabezado de la licencia en el archivo correspondiente para estar seguro.
