package com.pengpeng04.open;

import com.intellij.openapi.components.ApplicationComponent;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;

/**
 * author : pengpeng
 * date : 16/11/16
 * email : 194312815@qq.com
 **/
public class VersionApplicationComponent implements ApplicationComponent {

    public static final SAXBuilder saxBuilder = new SAXBuilder();
    public static final XMLOutputter xmlOutputter = new XMLOutputter();

    public VersionApplicationComponent() {
    }
    
    @Override
    public void initComponent() {
    }
    
    @Override
    public void disposeComponent() {
    }
    
    @Override
    @NotNull
    public String getComponentName() {
        return Constants.COMPONENT_NAME;
    }
}
