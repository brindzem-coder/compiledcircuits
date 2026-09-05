package com.example.compiledcircuits.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FolderNode {

    private final int id;

    private String name;
    private final int parentId;

    private final List<FolderNode> children =
            new ArrayList<>();

    private boolean expanded = true;

    public FolderNode(
            int id,
            String name,
            int parentId
    ) {

        this.id = id;
        this.name = name;
        this.parentId = parentId;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getParentId() {
        return parentId;
    }

    public List<FolderNode> getChildren() {
        return children;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void toggleExpanded() {
        expanded = !expanded;
    }

    public void addChild(
            FolderNode node
    ) {

        children.add(node);

        children.sort(
                Comparator.comparing(
                        FolderNode::getName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );
    }
}