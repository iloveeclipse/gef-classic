package org.eclipse.gef.ui.parts;
/*
 * Licensed Material - Property of IBM
 * (C) Copyright IBM Corp. 2001, 2002 - All Rights Reserved.
 * US Government Users Restricted Rights - Use, duplication or disclosure
 * restricted by GSA ADP Schedule Contract with IBM Corp.
 */

import java.util.*;

import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;

import org.eclipse.jface.util.Assert;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.*;

import org.eclipse.gef.*;
import org.eclipse.gef.dnd.*;

abstract public class AbstractEditPartViewer
	implements EditPartViewer, IMenuListener
{

protected final List
	selection = new ArrayList(),
	constantSelection = Collections.unmodifiableList(selection);

protected List selectionListeners = new ArrayList(1);

private EditPartFactory factory;
private Map mapIDToEditPart = new HashMap();
private Map mapVisualToEditPart = new HashMap();
private Control control;
private EditDomain domain;
private RootEditPart rootEditPart;
private EditPart focusPart;
private ContextMenuProvider contextMenuProvider;
private DelegatingDragAdapter dragAdapter = new DelegatingDragAdapter();
private DragSource dragSource;
private DelegatingDropAdapter dropAdapter = new DelegatingDropAdapter();
private DropTarget dropTarget;
private KeyHandler keyHandler;

public AbstractEditPartViewer(){
	init();
}

public void addSelectionChangedListener(ISelectionChangedListener listener){
	selectionListeners.add(listener);
}

public void addDragSourceListener(TransferDragSourceListener listener) {
	getDelegatingDragAdapter().addDragSourceListener(listener);
	refreshDragSourceAdapter();
}

public void addDropTargetListener(TransferDropTargetListener listener) {
	getDelegatingDropAdapter().addDropTargetListener(listener);
	refreshDropTargetAdapter();
}

public void appendSelection(EditPart editpart){
	if (editpart != focusPart)
		setFocus(null);
	List list = primGetSelectedEditParts();
	if (!list.isEmpty()){
		EditPart primary = (EditPart)list.get(list.size()-1);
		primary.setSelected(EditPart.SELECTED);
	}
	if (list.contains(editpart))
		list.remove(editpart);
	list.add(editpart);
	editpart.setSelected(EditPart.SELECTED_PRIMARY);
	
	//This order was intentionally changed so that synchronizing with the Outline would not affect Accessibility clients.
	fireSelectionChanged();
	expose(editpart);
}

/**
 * This assumes that the subclass has created the control and
 * simply returns the current control.  When overriding this
 * method, call <code>setControl(Control)</code> with the newly
 * created control and then call <code>super.createControl(Composite)</code>.
 */
public abstract Control createControl(Composite parent);

public void deselect(EditPart editpart){
	editpart.setSelected(EditPart.SELECTED_NONE);

	List selection = primGetSelectedEditParts();
	selection.remove(editpart);
	if (!selection.isEmpty()){
		EditPart primary = (EditPart)selection.get(selection.size()-1);
		primary.setSelected(EditPart.SELECTED_PRIMARY);
	}
	fireSelectionChanged();
}

public void deselectAll(){
	EditPart part;
	List list = primGetSelectedEditParts();
	setFocus(null);
	for (int i=0; i<list.size(); i++){
		part = (EditPart)list.get(i);
		part.setSelected(EditPart.SELECTED_NONE);
	}
	list.clear();
	fireSelectionChanged();
}

public void dispose(){
	primDeselectAll();
	if (getControl() != null && !getControl().isDisposed())
		getControl().dispose();
	setControl(null);
}

protected void expose(EditPart part){
}

protected void fireSelectionChanged() {
	Iterator iter = selectionListeners.iterator();
	SelectionChangedEvent event = new SelectionChangedEvent(this, getSelection());
	while(iter.hasNext()) {
		ISelectionChangedListener l = (ISelectionChangedListener)iter.next();
		l.selectionChanged(event);
	}
}

public void flush(){}

protected ContextMenuProvider getContextMenuProvider(){
	return contextMenuProvider;
}

public EditPart getContents(){
	return getRootEditPart().getContents();
}

public Control getControl(){
	return control;
}

protected DelegatingDragAdapter getDelegatingDragAdapter() {
	return dragAdapter;
}

protected DelegatingDropAdapter getDelegatingDropAdapter() {
	return dropAdapter;
}

protected DragSource getDragSource() {
	return dragSource;
}

protected DropTarget getDropTarget() {
	return dropTarget;
}

public EditDomain getEditDomain(){
	return domain;
}

public EditPartFactory getEditPartFactory(){
	return factory;
}

public Map getEditPartRegistry(){
	return mapIDToEditPart;
}

public EditPart getFocusEditPart(){
	if (focusPart != null)
		return focusPart;
	if (getSelectedEditParts().isEmpty() && getContents() != null)
		return getContents();	
	return (EditPart)getSelectedEditParts().get(0);
}

public KeyHandler getKeyHandler(){
	return keyHandler;
}

public RootEditPart getRootEditPart(){
	return rootEditPart;
}

public List getSelectedEditParts(){
	return constantSelection;
}

public ISelection getSelection(){
	if (getSelectedEditParts().isEmpty() && getContents() != null)
		return new StructuredSelection(getContents());
	return new StructuredSelection(getSelectedEditParts());
}

public Map getVisualPartMap(){
	return mapVisualToEditPart;
}

protected void hookControl(){
	if (getControl() == null)
		return;
	MenuManager manager = new MenuManager();
	manager.addMenuListener(this);
	manager.setRemoveAllWhenShown(true);
	Menu menu = manager.createContextMenu(control);
	control.setMenu(menu);
	if (getRootEditPart() != null)
		getRootEditPart().activate();
	refreshDragSourceAdapter();
	refreshDropTargetAdapter();
}

protected void hookDragSource(){
	dragSource.addDragListener(getDelegatingDragAdapter());
}

protected void hookDropTarget(){
	getDropTarget().addDropListener(getDelegatingDropAdapter());
}

protected void init(){}

/**
 * Called just before the context menu is shown.
 */
public void menuAboutToShow(IMenuManager menu) {
	if (getContextMenuProvider() != null)
		getContextMenuProvider().buildContextMenu(menu, this);
}

private void primDeselectAll(){
	EditPart part;
	List list = primGetSelectedEditParts();
	for (int i=0; i<list.size(); i++){
		part = (EditPart)list.get(i);
		part.setSelected(EditPart.SELECTED_NONE);
	}
	list.clear();
}

protected List primGetSelectedEditParts(){
	return selection;
}

protected void refreshDragSourceAdapter() {
	if (getControl() == null)
		return;
	if (getDelegatingDragAdapter().isEmpty())
		setDragSource(null);
	else
		if (getDragSource() == null)
			setDragSource(new DragSource(getControl(), DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK));
}

protected void refreshDropTargetAdapter() {
	if (getControl() == null)
		return;
	if (getDelegatingDropAdapter().isEmpty())
		setDropTarget(null);
	else {
		if (getDropTarget() == null)
			setDropTarget(new DropTarget(getControl(), DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK));
		getDropTarget().setTransfer(getDelegatingDropAdapter().getTransferTypes());
	}
}

public void registerAccessibleEditPart(AccessibleEditPart acc) {}

public void removeDragSourceListener(TransferDragSourceListener listener) {
	getDelegatingDragAdapter().removeDragSourceListener(listener);
	if (getDelegatingDragAdapter().isEmpty())
		refreshDragSourceAdapter();
}

public void removeDropTargetListener(TransferDropTargetListener listener) {
	getDelegatingDropAdapter().removeDropTargetListener(listener);
	if (getDelegatingDropAdapter().isEmpty())
		refreshDropTargetAdapter();
}

public void removeSelectionChangedListener(ISelectionChangedListener l){
	selectionListeners.remove(l);
}

public void select(EditPart editpart){
	// If selection isn't changing, do nothing.
	if ((getSelectedEditParts().size() == 1) && 
		(getSelectedEditParts().get(0) == editpart))
			return;
	primDeselectAll();
	appendSelection(editpart);  // fireSelectionChanged() is called here
}

public void setContextMenuProvider(ContextMenuProvider provider){
	contextMenuProvider = provider;
}

public void setContents(EditPart editpart){
	getRootEditPart().setContents(editpart);
}

public void setContents(Object contents){
	Assert.isTrue(getEditPartFactory() != null,
		"An EditPartFactory is required to call setContents(Object)");//$NON-NLS-1$
	setContents(getEditPartFactory().
			createEditPart(null, contents));
}

public void setControl(Control control){
	if (this.control != null)
		unhookControl();
	this.control = control;
	if (control != null)
		hookControl();
}

public void setCursor(Cursor cursor){
	if (getControl() == null || getControl().isDisposed())
		return;
	getControl().setCursor(cursor);
}

protected void setDragSource(DragSource source){
	if (dragSource != null)
		dragSource.dispose();
	dragSource = source;
	if (dragSource != null)
		hookDragSource();
}

/**
 * Sets the dropTarget.
 * @param dropTarget The dropTarget to set
 */
protected void setDropTarget(DropTarget target) {
	if (dropTarget != null)
		dropTarget.dispose();
	dropTarget = target;
	if (dropTarget != null)
		hookDropTarget();
}

public void setEditDomain(EditDomain editdomain){
	this.domain = editdomain;
}

public void setEditPartFactory(EditPartFactory factory){
	this.factory = factory;
}

public void setFocus(EditPart part){
	if (focusPart == part)
		return;
	if (focusPart != null)
		focusPart.setFocus(false);
	focusPart = part;
	if (focusPart != null){
		focusPart.setFocus(true);
		expose(focusPart);
	}
}

public void setKeyHandler(KeyHandler handler){
	keyHandler = handler;
}

public void setRootEditPart(RootEditPart editpart){
	rootEditPart = editpart;
	rootEditPart.setViewer(this);
	if (getControl() != null)
		rootEditPart.activate();
}

public void setRouteEventsToEditDomain(boolean value){}

/**
 * This method will clear the current selection, and set
 * the given ISelection as the current selection.  It will
 * do so without firing a selection-change event.
 * NOTE: If the given argument is not an IStructuredSelection,
 * this method will do nothing.
 */
public void setSelection(ISelection newSelection){
	if( !(newSelection instanceof IStructuredSelection) ){
		return;
	}
	List editparts = ((IStructuredSelection)newSelection).toList();
	List selection = primGetSelectedEditParts();

	setFocus(null);
	for (int i=0; i<selection.size(); i++)
		((EditPart)selection.get(i)).setSelected(EditPart.SELECTED_NONE);
	selection.clear();

	for (int i=0; i<editparts.size(); i++){
		EditPart part = (EditPart)editparts.get(i);
		selection.add(part);
		if (i==editparts.size()-1){
			part.setSelected(EditPart.SELECTED_PRIMARY);
			expose(part);
		}
		else
			part.setSelected(EditPart.SELECTED);
	}
	fireSelectionChanged();
}

protected void unhookControl(){
	if( getControl() == null ){
		return;
	}
	if( getRootEditPart() != null ){
		getRootEditPart().deactivate();
	}
}

public void unregisterAccessibleEditPart(AccessibleEditPart acc) {}

}