package com.pengpeng04.open.actions;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.pengpeng04.open.Constants;
import com.pengpeng04.open.VersionApplicationComponent;
import com.yourkit.util.Strings;
import org.jdom.Document;
import org.jdom.Element;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class VersionAction extends AnAction {

    private boolean isExistFile(String path) {
        if (Strings.isNullOrEmpty(path)) {
            return false;
        }
        File file = new File(path);
        return file.exists();
    }

    private Document getPomDocument(String pomFilePath) {
        try (InputStream file = new FileInputStream(pomFilePath)) {
            Document document = VersionApplicationComponent.saxBuilder.build(file);
            return document;
        } catch (Exception ex) {
            return null;
        }
    }

    private String getParentPomVersion(Document document) {
        Element rootElement = document.getRootElement();
        if (null == rootElement) {
            return null;
        }
        List<Element> elementList = rootElement.getChildren(Constants.POM_NODE_VERSION, rootElement.getNamespace());
        if (null != elementList || !elementList.isEmpty()) {
            return elementList.get(0).getValue();
        } else {
            return null;
        }
    }

    private List<String> getSubModuleList(Document document) {
        Element rootElement = document.getRootElement();
        if (null == rootElement) {
            return null;
        }
        List<Element> modulesElementList = rootElement.getChildren(Constants.POM_NODE_MODULES, rootElement.getNamespace());
        List<String> subModules = Lists.newArrayList();
        if (null == modulesElementList || modulesElementList.isEmpty()) {
            return subModules;
        }
        Element modules = modulesElementList.get(0);
        for (Element element : modules.getChildren()) {
            subModules.add(element.getValue());
        }
        return subModules;
    }

    private Map<String, String> getPomPropertiesNodeMap(Document document) {
        Map<String, String> result = Maps.newHashMap();
        Element rootElement = document.getRootElement();
        if (null == rootElement) {
            return result;
        }
        List<Element> propertiesElementList = rootElement.getChildren(Constants.POM_NODE_PROPERTIES, rootElement.getNamespace());
        if (null != propertiesElementList && !propertiesElementList.isEmpty()) {
            Element propertiesNode = propertiesElementList.get(0);
            List<Element> propertiesNodeChildren = propertiesNode.getChildren();
            propertiesNodeChildren = (null == propertiesNodeChildren) ? Lists.newArrayList() : propertiesNodeChildren;
            for (Element element : propertiesNodeChildren) {
                result.put(element.getName().trim(), element.getTextTrim());
            }
        }
        return result;
    }

    private void updatePomPropertiesNode(Document document, Map<String, String> pomPropertiesMap) {
        Element rootElement = document.getRootElement();
        if (null == rootElement) {
            return;
        }
        List<Element> propertiesElementList = rootElement.getChildren(Constants.POM_NODE_PROPERTIES, rootElement.getNamespace());
        if (null == propertiesElementList || propertiesElementList.isEmpty()
                || null == pomPropertiesMap || pomPropertiesMap.isEmpty()) {
            return;
        }
        Element propertiesNode = propertiesElementList.get(0);
        List<Element> propertiesNodeChildren = propertiesNode.getChildren();
        propertiesNodeChildren = (null == propertiesNodeChildren) ? Lists.newArrayList() : propertiesNodeChildren;
        for (Element element : propertiesNodeChildren) {
            if (pomPropertiesMap.containsKey(element.getName().trim())) {
                element.setText(pomPropertiesMap.get(element.getName().trim()));
            }
        }
    }

    private String parseELExpressionVersion(String version) {
        if (version.startsWith(Constants.POM_NODE_VERSION_EL_EXPRESSION_PREFIX)) {
            return version.substring(Constants.POM_NODE_VERSION_EL_EXPRESSION_PREFIX.length(), version.length() - 1);
        } else {
            return "";
        }
    }

    private void updateDependencyNodeVersion(String newVersion, Element dependenciesNode, Set<String> moduleSetHash, final Map<String, String> pomPropertiesNodeMap) {
        List<Element> elementList = dependenciesNode.getChildren(Constants.POM_NODE_DEPENDENCY, dependenciesNode.getNamespace());
        if (null == elementList || elementList.isEmpty()) {
            return;
        }
        for (Element element : elementList) {
            List<Element> artifactIdList = element.getChildren(Constants.POM_NODE_ARTIFACTID, dependenciesNode.getNamespace());
            artifactIdList = (null == artifactIdList) ? Lists.newArrayList() : artifactIdList;
            if (!artifactIdList.isEmpty()) {
                Element artifactId = artifactIdList.get(0);
                if (moduleSetHash.contains(artifactId.getValue().trim())) {
                    List<Element> versoinList = element.getChildren(Constants.POM_NODE_VERSION, dependenciesNode.getNamespace());
                    versoinList = (null == versoinList) ? Lists.newArrayList() : versoinList;
                    versoinList.forEach(new Consumer<Element>() {
                        @Override
                        public void accept(Element element) {
                            String version = element.getValue();
                            version = (null == version) ? "" : version.trim();
                            if (version.startsWith(Constants.POM_NODE_VERSION_EL_EXPRESSION_PREFIX)) { //说明是EL表达式
                                version = parseELExpressionVersion(version);
                                if (pomPropertiesNodeMap.containsKey(version)) {
                                    pomPropertiesNodeMap.put(version, newVersion);
                                    return;
                                }
                            }
                            element.setText(newVersion);
                        }
                    });
                }
            }
        }
    }


    public void updateRootPomVersion(String newVersion, Document document) {
        Element rootElement = document.getRootElement();
        if (null == rootElement) {
            return;
        }

        List<Element> versionElementList = rootElement.getChildren(Constants.POM_NODE_VERSION, rootElement.getNamespace());
        versionElementList = (null == versionElementList) ? Lists.newArrayList() : versionElementList;
        for (Element element : versionElementList) {
            element.setText(newVersion);
        }
    }


    public void updatePomVersion(String newVersion, List<String> subModuleList, Document document, Map<String, String> parentPomPropertiesMap) {
        Element rootElement = document.getRootElement();
        if (null == rootElement) {
            return;
        }
        subModuleList = (null == subModuleList) ? Lists.newArrayList() : subModuleList;
        Set<String> moduleSetHash = Sets.newHashSet();
        for (String module : subModuleList) {
            moduleSetHash.add(module);
        }
        parentPomPropertiesMap = (null == parentPomPropertiesMap) ? Maps.newHashMap() : parentPomPropertiesMap;
        Map<String, String> currentPomPropertiesMap = Maps.newHashMap();
        currentPomPropertiesMap.putAll(parentPomPropertiesMap);
        currentPomPropertiesMap.putAll(getPomPropertiesNodeMap(document));
        List<Element> versionElementList = rootElement.getChildren(Constants.POM_NODE_VERSION, rootElement.getNamespace());
        versionElementList = (null == versionElementList) ? Lists.newArrayList() : versionElementList;
        for (Element element : versionElementList) {
            element.setText(newVersion);
        }
        List<Element> parentNodeList = rootElement.getChildren(Constants.POM_NODE_PARENT, rootElement.getNamespace());
        if (null != parentNodeList && !parentNodeList.isEmpty()) {
            Element parentNode = parentNodeList.get(0);
            List<Element> parentNodeVersionList = parentNode.getChildren(Constants.POM_NODE_VERSION, rootElement.getNamespace());
            if (null != parentNodeVersionList && !parentNodeVersionList.isEmpty()) {
                parentNodeVersionList.get(0).setText(newVersion);
            }
        }
        List<Element> dependenciesElementList = rootElement.getChildren(Constants.POM_NODE_DEPENDENCIES, rootElement.getNamespace());
        if (null != dependenciesElementList && !dependenciesElementList.isEmpty()) {
            Element dependenciesNode = dependenciesElementList.get(0);
            updateDependencyNodeVersion(newVersion, dependenciesNode, moduleSetHash, currentPomPropertiesMap);
        }
        List<Element> dependencyManagementList = rootElement.getChildren(Constants.POM_NODE_DEPENDENCY_MANAGEMENT, rootElement.getNamespace());
        if (null != dependencyManagementList && !dependencyManagementList.isEmpty()) {
            Element dependencyManagement = dependencyManagementList.get(0);
            dependenciesElementList = dependencyManagement.getChildren(Constants.POM_NODE_DEPENDENCIES, rootElement.getNamespace());
            if (null != dependenciesElementList && !dependenciesElementList.isEmpty()) {
                Element dependenciesNode = dependenciesElementList.get(0);
                updateDependencyNodeVersion(newVersion, dependenciesNode, moduleSetHash, currentPomPropertiesMap);
            }
        }
        for (String key : parentPomPropertiesMap.keySet()) {
            if (currentPomPropertiesMap.containsKey(key)) {
                parentPomPropertiesMap.put(key, currentPomPropertiesMap.get(key));
            }
        }
        List<Element> propertiesElementList = rootElement.getChildren(Constants.POM_NODE_PROPERTIES, rootElement.getNamespace());
        if (null != propertiesElementList && !propertiesElementList.isEmpty()) {
            Element propertiesNode = propertiesElementList.get(0);
            List<Element> propertiesNodeChildren = propertiesNode.getChildren();
            propertiesNodeChildren = (null == propertiesNodeChildren) ? Lists.newArrayList() : propertiesNodeChildren;
            for (Element element : propertiesNodeChildren) {
                if (currentPomPropertiesMap.containsKey(element.getName().trim())) {
                    element.setText(currentPomPropertiesMap.get(element.getName().trim()));
                }
            }
        }
    }

    public void writeNewPomFile(Document document, String path) {
        if (Strings.isNullOrEmpty(path)) {
            return;
        }
        if (isExistFile(path)) {
            new File(path).delete();
        }
        try (OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(path), Charsets.UTF_8.name())) {
            VersionApplicationComponent.xmlOutputter.output(document, osw);
        } catch (Exception ex) {
        }
    }

    private void refreshActiveEditor(Project project) {
        VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
        final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (null == editor) {
            return;
        }
        final com.intellij.openapi.editor.Document document = editor.getDocument();
        if (null == document) {
            return;
        }
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
        if (null == virtualFile) {
            return;
        }
        com.intellij.openapi.editor.Document newDocument = FileDocumentManager.getInstance().getDocument(virtualFile);
        if (null == newDocument) {
            return;
        }
        Runnable updateDocRunner = new Runnable() {
            @Override
            public void run() {
                editor.getDocument().setText(newDocument.getText());
            }
        };
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                ApplicationManager.getApplication().runWriteAction(updateDocRunner);
            }
        });
    }

    private Module getParentModule(Module module) {
        Module[] modules = ModuleManager.getInstance(module.getProject()).getModules();
        Set<Module> moduleSet = Sets.newHashSet();
        if (null != modules) {
            for (Module pModule : modules) {
                moduleSet.add(pModule);
            }
        }
        VirtualFile moduleVirtualFile = module.getModuleFile();
        Module parentModule = module;
        while (moduleSet.contains(module)) {
            moduleVirtualFile = moduleVirtualFile.getParent();
            parentModule = module;
            module = ModuleUtil.findModuleForFile(moduleVirtualFile, module.getProject());
        }
        return parentModule;
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Presentation presentation = e.getPresentation();
        if (!presentation.isEnabled()) {
            return;
        }
        Project project = e.getProject();
        if (null == project) {
            presentation.setVisible(false);
            return;
        }
        String projectBasePath = project.getBasePath();
        if (Strings.isNullOrEmpty(projectBasePath)) {
            presentation.setVisible(false);
            return;
        }
        VirtualFile selectedVirtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        if (null == selectedVirtualFile || !selectedVirtualFile.isDirectory()) {
            presentation.setVisible(false);
            return;
        }
        Module module = ModuleUtil.findModuleForFile(selectedVirtualFile, project);
        if (null != module) {
            Module parentModule = getParentModule(module);
            if (!parentModule.getModuleFile().getParent().getPath().equalsIgnoreCase(selectedVirtualFile.getPath())) {
                presentation.setVisible(false);
                return;
            }
        }
        String parentPomFilePath = projectBasePath + File.separator + Constants.POM_FILE_NAME;
        if (!isExistFile(parentPomFilePath)) {
            presentation.setVisible(false);
            return;
        } else {
            presentation.setVisible(true);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (null == project) {
            return;
        }
        VirtualFile selectedVirtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        if (null == selectedVirtualFile) {
            return;
        }
        String projectBasePath = selectedVirtualFile.getPath();
        if (Strings.isNullOrEmpty(projectBasePath)) {
            return;
        }
        String parentPomFilePath = projectBasePath + File.separator + Constants.POM_FILE_NAME;
        if (!isExistFile(parentPomFilePath)) {
            return;
        }
        String parentPomVersion = "";
        Document parentPomDocument;
        List<String> subModuleList;
        parentPomDocument = getPomDocument(parentPomFilePath);
        parentPomVersion = getParentPomVersion(parentPomDocument);
        subModuleList = getSubModuleList(parentPomDocument);
        if (Strings.isNullOrEmpty(parentPomVersion) || null == parentPomDocument) {
            return;
        }
        String newVersion = Messages.showInputDialog("新版本:", "当前版本(" + parentPomVersion + ")", null);
        newVersion = null == newVersion ? "" : newVersion;
        newVersion = newVersion.trim();
        if (Strings.isNullOrEmpty(newVersion)) {
            return;
        }
        Map<String, String> parentPomPropertiesMap = getPomPropertiesNodeMap(parentPomDocument);
        subModuleList = (null == subModuleList) ? Lists.newArrayList() : subModuleList;
        updateRootPomVersion(newVersion, parentPomDocument);
        for (String module : subModuleList) {
            String modulePath = projectBasePath + File.separator + module + File.separator + Constants.POM_FILE_NAME;
            Document moduleDocument = getPomDocument(modulePath);
            updatePomVersion(newVersion, subModuleList, moduleDocument, parentPomPropertiesMap);
            writeNewPomFile(moduleDocument, modulePath);
        }
        updatePomPropertiesNode(parentPomDocument, parentPomPropertiesMap);
        writeNewPomFile(parentPomDocument, parentPomFilePath);
        refreshActiveEditor(project);
    }
}
