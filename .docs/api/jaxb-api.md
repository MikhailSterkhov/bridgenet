# Bridgenet / API / JAXB

JAXB - Внутреннее API системы Bridgenet для парсинга XML-конфигураций
<br>в типизированные Java-объекты (дескрипторы). Реализовано в модуле `assembly`
<br>(пакет `me.moonways.bridgenet.assembly.jaxb`) поверх стандартного `javax.xml.bind`
<br>и используется системой для чтения конфигураций из общего каталога ресурсов `etc`.

---

## API

Ядро API состоит из двух элементов:

- `me.moonways.bridgenet.assembly.jaxb.XmlJaxbParser` - парсер, который создает
  <br>`JAXBContext` по типу дескриптора и десериализует (unmarshal) содержимое
  <br>`InputStream` в объект этого типа. В случае ошибки парсинга - логирует ее
  <br>и возвращает `null`;
- `me.moonways.bridgenet.assembly.jaxb.XmlRootObject` - маркерный интерфейс,
  <br>которым обязан быть помечен класс корневого XML-дескриптора.

Единственный публичный метод парсера:

```java
public <X extends XmlRootObject> X parseToDescriptorByType(InputStream inputStream, Class<X> cls)
```

Сами дескрипторы описываются стандартными аннотациями JAXB из пакета
<br>`javax.xml.bind.annotation`:

- `@XmlRootElement(name = "...")` - имя корневого тега XML-документа;
- `@XmlElement(name = "...")` - маппинг поля на вложенный тег;
- `@XmlElementWrapper(name = "...")` - тег-обертка для списков элементов;
- `@XmlType(propOrder = {...})` - порядок следования тегов внутри элемента.

Так как проект использует Lombok, JAXB-аннотации навешиваются на сеттеры
<br>через `@Setter(onMethod_ = ...)`.

Корневые дескрипторы, уже реализованные в системе:

| Дескриптор                                                              | Ресурс                | Кто читает                                                     |
|-------------------------------------------------------------------------|-----------------------|----------------------------------------------------------------|
| `me.moonways.bridgenet.bootstrap.xml.XMLBootstrapConfigDescriptor`       | `bootstrap.xml`       | `me.moonways.bridgenet.bootstrap.hook.BootstrapHookContainer`   |
| `me.moonways.bridgenet.rmi.xml.XMLServicesConfigDescriptor`              | `remote_services.xml` | `me.moonways.bridgenet.rmi.service.RemoteServicesManagement`    |
| `me.moonways.bridgenet.api.inject.decorator.config.XMLInterceptorDescriptor` | `decorators.xml`  | `me.moonways.bridgenet.api.inject.decorator.DecoratedMethodScanner` |
| `me.moonways.bridgenet.rest.server.jaxb.JaxbServerContext`               | `rest_server.xml`     | `me.moonways.bridgenet.rest.server.WrappedHttpServer`           |

XML-ресурсы лежат в директории 'etc' модуля `assembly` и при сборке проекта
<br>попадают в директорию 'etc' в папке со сборкой '.build' (подробнее - в
<br>документации модуля `assembly`). Наименования ресурсов хранятся в виде
<br>констант в классе `me.moonways.bridgenet.assembly.ResourcesTypes`.

---

## USAGE

Для того чтобы читать XML-конфигурации, необходимо проинжектить
<br>основной сервис модуля `assembly`:

```java

@Inject
private ResourcesAssembly assembly;
```

Для **чтения XML-ресурса в готовый дескриптор** мы можем использовать
<br>следующий функционал:

```java
XMLServicesConfigDescriptor xmlConfiguration =
        assembly.readXmlAtEntity(ResourcesTypes.REMOTE_SERVICES_XML, XMLServicesConfigDescriptor.class);

List<XmlServiceInfoDescriptor> servicesList = xmlConfiguration.getServicesList();
```

Метод `readXmlAtEntity` под капотом сам открывает `InputStream` ресурса
<br>и делегирует его в `XmlJaxbParser`. Экземпляр парсера также доступен
<br>напрямую из сервиса `ResourcesAssembly`:

```java
XmlJaxbParser parser = assembly.getXmlJaxbParser();

XMLBootstrapConfigDescriptor descriptor = parser.parseToDescriptorByType(
        assembly.readResourceStream(ResourcesTypes.BOOTSTRAP_XML),
        XMLBootstrapConfigDescriptor.class);
```

Для **объявления собственного дескриптора** необходимо реализовать
<br>маркерный интерфейс `XmlRootObject` и разметить класс JAXB-аннотациями.
<br>Реальный пример из системы - дескриптор конфигурации RMI-сервисов
<br>`me.moonways.bridgenet.rmi.xml.XMLServicesConfigDescriptor`:

```java
@Getter
@ToString
@XmlRootElement(name = "configuration")
public class XMLServicesConfigDescriptor implements XmlRootObject {

    @Setter(onMethod_ = {
            @XmlElementWrapper(name = "modules"),
            @XmlElement(name = "module")
    })
    private List<XMLServiceModuleDescriptor> modulesList;

    @Setter(onMethod_ = {
            @XmlElementWrapper(name = "services"),
            @XmlElement(name = "service")
    })
    private List<XmlServiceInfoDescriptor> servicesList;
}
```

Вложенные элементы описываются отдельными классами, которым интерфейс
<br>`XmlRootObject` уже не требуется:

```java
@Getter
@ToString
@XmlType(propOrder = {"bindPort", "name", "modelPath"})
@XmlRootElement(name = "service")
public class XmlServiceInfoDescriptor {

    @Setter(onMethod_ = @XmlElement)
    private String bindPort;

    @Setter(onMethod_ = @XmlElement)
    private String name;

    @Setter(onMethod_ = @XmlElement)
    private String modelPath;
}
```

Такому дескриптору соответствует следующая структура XML-документа
<br>(фрагмент реального ресурса `remote_services.xml`):

```xml

<configuration>
    <services>
        <service>
            <!-- RMI Protocol service bind port -->
            <bindPort>7000</bindPort>
            <!-- Service direction name -->
            <name>auth</name>
            <!-- Target service class type -->
            <modelPath>me.moonways.bridgenet.model.service.auth.AuthServiceModel</modelPath>
        </service>
    </services>
</configuration>
```

Пример полного цикла использования из системы - контейнер хуков запуска
<br>`me.moonways.bridgenet.bootstrap.hook.BootstrapHookContainer`, который
<br>читает конфигурацию `bootstrap.xml` и разбирает список хуков:

```java
private XMLBootstrapConfigDescriptor parseConfiguration() {
    return assembly.readXmlAtEntity(ResourcesTypes.BOOTSTRAP_XML,
            XMLBootstrapConfigDescriptor.class);
}
```

```java
XMLBootstrapConfigDescriptor xmlBootstrap = parseConfiguration();
List<XMLHookDescriptor> hooks = xmlBootstrap.getHooks();
```
