package me.moonways.bridgenet.injection.xml.element;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@Getter
@ToString
@XmlRootElement(name = "factory")
@XmlType(propOrder = {"annotationClass", "targetClass"})
public class XMLObjectFactory {

    @Setter(onMethod_ = @XmlElement)
    private String annotationClass;

    @Setter(onMethod_ = @XmlElement)
    private String targetClass;
}
