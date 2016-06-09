// The MIT License (MIT)
//
// Copyright (c) 2015, 2016 Arian Fornaris
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to permit
// persons to whom the Software is furnished to do so, subject to the
// following conditions: The above copyright notice and this permission
// notice shall be included in all copies or substantial portions of the
// Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
// OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
// NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
// OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
// USE OR OTHER DEALINGS IN THE SOFTWARE.
package phasereditor.canvas.ui.editors.behaviors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;

import javafx.collections.ObservableList;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import phasereditor.canvas.ui.editors.ObjectCanvas;
import phasereditor.canvas.ui.editors.SelectionBoxNode;
import phasereditor.canvas.ui.editors.SelectionNode;
import phasereditor.canvas.ui.shapes.GroupNode;
import phasereditor.canvas.ui.shapes.IObjectNode;
import phasereditor.canvas.ui.shapes.ISpriteNode;

/**
 * @author arian
 *
 */
public class SelectionBehavior implements ISelectionProvider {
	private ObjectCanvas _canvas;
	private ListenerList _listenerList;
	private IStructuredSelection _selection;
	private List<IObjectNode> _selectedNodes;
	private SelectionBoxNode _selectionBox;
	private Point2D _boxStart;

	public SelectionBehavior(ObjectCanvas canvas) {
		super();
		_canvas = canvas;
		_selection = StructuredSelection.EMPTY;
		_selectedNodes = new ArrayList<>();
		_listenerList = new ListenerList(ListenerList.IDENTITY);

		_canvas.getOutline().addSelectionChangedListener(new ISelectionChangedListener() {

			@SuppressWarnings("synthetic-access")
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				if (!event.getSelection().isEmpty()) {
					try {
						setSelection_private(event.getSelection());
					} catch (Exception e) {
						throw e;
					}
				}
			}
		});
	}

	private Node pickNode(Node test, double sceneX, double sceneY) {
		if (test instanceof IObjectNode) {
			if (!((IObjectNode) test).getModel().isEditorPick()) {
				return null;
			}
		}

		if (test instanceof GroupNode) {
			ObservableList<Node> list = ((GroupNode) test).getChildren();
			for (int i = list.size() - 1; i >= 0; i--) {
				Node child = list.get(i);
				Node result = pickNode(child, sceneX, sceneY);
				if (result != null) {
					return result;
				}
			}
		}

		Point2D p = test.sceneToLocal(sceneX, sceneY);
		if (test.contains(p)) {
			return test;
		}

		return null;
	}

	void handleMouseReleased(MouseEvent e) {
		if (isSelectingBox()) {
			_canvas.getSelectionGlassPane().getChildren().remove(_selectionBox);
			selectBox(_selectionBox);
			_selectionBox = null;
			return;
		}

		Node userPicked = pickNode(_canvas.getWorldNode(), e.getSceneX(), e.getSceneY());

		Node picked = findBestToPick(userPicked);

		if (picked == null) {
			setSelection(StructuredSelection.EMPTY);
			return;
		}

		if (isSelected(picked)) {
			if (e.isControlDown()) {
				removeNodeFromSelection(picked);
			}
			return;
		}

		if (_selection != null && !_selection.isEmpty() && e.isControlDown()) {
			HashSet<Object> selection = new HashSet<>(Arrays.asList(_selection.toArray()));
			selection.add(picked);
			setSelection(new StructuredSelection(selection.toArray()));
		} else {
			setSelection(new StructuredSelection(picked));
		}
	}

	void handleDragDetected(MouseEvent e) {
		Pane glassPane = _canvas.getSelectionGlassPane();
		Point2D point = glassPane.sceneToLocal(e.getSceneX(), e.getSceneY());
		_boxStart = point;
		_selectionBox = new SelectionBoxNode();
		_selectionBox.setBox(point, point.add(0, 0));
		glassPane.getChildren().add(_selectionBox);
	}

	public boolean isPointingToSelection(MouseEvent e) {
		for (IObjectNode inode : _selectedNodes) {
			Node node = inode.getNode();
			Bounds b = node.getBoundsInLocal();
			if (node.localToScene(b).contains(e.getSceneX(), e.getSceneY())) {
				return true;
			}
		}
		return false;
	}

	void handleMouseDragged(MouseEvent e) {
		if (_selectionBox != null) {
			Point2D point = _canvas.getSelectionGlassPane().sceneToLocal(e.getSceneX(), e.getSceneY());
			_selectionBox.setBox(_boxStart, point);
		}
	}

	private void selectBox(SelectionBoxNode selectionBox) {
		List<Object> list = new ArrayList<>();
		Bounds selBounds = selectionBox.localToScene(selectionBox.getBoundsInLocal());

		_canvas.getWorldNode().walkTree(inode -> {
			if (!inode.getModel().isEditorPick()) {
				return;
			}

			if (inode instanceof GroupNode && !((GroupNode) inode).getModel().isEditorClosed()) {
				// do not select open groups, else the children of the group
				return;
			}

			Node node = inode.getNode();
			Bounds b = node.localToScene(node.getBoundsInLocal());
			if (selBounds.contains(b)) {
				list.add(inode);
			}
		} , false);

		setSelection(new StructuredSelection(list));
	}

	public Node findBestToPick(Node picked) {
		if (picked == null) {
			return null;
		}

		if (picked == _canvas.getWorldNode()) {
			return null;
		}

		GroupNode closed = findClosedParent(picked);

		if (closed != null) {
			return closed;
		}

		if (picked instanceof ISpriteNode) {
			return picked;
		}

		return findBestToPick(picked.getParent());
	}

	private GroupNode findClosedParent(Node picked) {
		if (picked == null) {
			return null;
		}

		if (picked == _canvas.getWorldNode()) {
			return null;
		}

		GroupNode closed = findClosedParent(picked.getParent());

		if (closed != null) {
			return closed;
		}

		if (picked instanceof GroupNode) {
			GroupNode group = (GroupNode) picked;
			if (group.getModel().isEditorClosed()) {
				return group;
			}
		}

		return null;
	}

	public boolean isSelectingBox() {
		return _selectionBox != null;
	}

	public boolean isSelected(Object node) {
		if (_selection == null) {
			return false;
		}

		for (Object e : _selection.toArray()) {
			if (e == node) {
				return true;
			}
		}

		return false;
	}

	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		_listenerList.add(listener);
	}

	@Override
	public IStructuredSelection getSelection() {
		return _selection;
	}

	public List<IObjectNode> getSelectedNodes() {
		return _selectedNodes;
	}

	public boolean containsInSelection(Node node) {
		return _selection != null && _selection.toList().contains(node);
	}

	@Override
	public void setSelection(ISelection selection) {
		setSelection_private(selection);
		TreeViewer outline = _canvas.getOutline();
		outline.setSelection(selection, true);
	}

	private void setSelection_private(ISelection selection) {
		{
			_selection = (IStructuredSelection) selection;
			List<IObjectNode> list = new ArrayList<>();
			for (Object obj : _selection.toArray()) {
				list.add((IObjectNode) obj);
			}
			_selectedNodes = list;
		}

		{
			Object[] list = _listenerList.getListeners();
			for (Object l : list) {
				((ISelectionChangedListener) l).selectionChanged(new SelectionChangedEvent(this, selection));
			}
		}

		updateSelectedNodes();
	}

	public void updateSelectedNodes() {
		Pane selpane = _canvas.getSelectionPane();

		selpane.getChildren().clear();

		for (Object obj : _selection.toArray()) {
			if (obj instanceof IObjectNode) {
				IObjectNode inode = (IObjectNode) obj;
				Node node = inode.getNode();

				Bounds rect = buildSelectionBounds(node);

				if (rect == null) {
					continue;
				}

				selpane.getChildren().add(new SelectionNode(_canvas, inode, rect));
			}
		}

		selpane.requestLayout();
	}

	private Bounds buildSelectionBounds(Node node) {
		List<Bounds> list = new ArrayList<>();

		buildSelectionBounds(node, list);

		if (list.isEmpty()) {
			return null;
		}

		Bounds first = list.get(0);

		double x0 = first.getMinX();
		double y0 = first.getMinY();
		double x1 = x0 + first.getWidth();
		double y1 = y0 + first.getHeight();

		for (Bounds r : list) {
			double r_x0 = r.getMinX();
			double r_x1 = r_x0 + r.getWidth();
			double r_y0 = r.getMinY();
			double r_y1 = r_y0 + r.getHeight();

			if (r_x0 < x0) {
				x0 = r_x0;
			}

			if (r_x1 > x1) {
				x1 = r_x1;
			}

			if (r_y0 < y0) {
				y0 = r_y0;
			}

			if (r_y1 > y1) {
				y1 = r_y1;
			}
		}

		return new BoundingBox(x0, y0, x1 - x0, y1 - y0);
	}

	private void buildSelectionBounds(Node node, List<Bounds> list) {
		GroupNode world = _canvas.getWorldNode();
		Bounds b = localToAncestor(node.getBoundsInLocal(), node, world);

		if (node instanceof GroupNode) {
			// add the children bounds
			for (Node child : ((GroupNode) node).getChildren()) {
				buildSelectionBounds(child, list);
			}
			// add the left corner of the group
			list.add(new BoundingBox(b.getMinX(), b.getMinY(), 1, 1));
		} else {
			list.add(b);
		}

	}

	public static Point2D localToAncestor(double x, double y, Node local, Node ancestor) {
		return localToAncestor(new Point2D(x, y), local.getParent(), ancestor);
	}

	public static Point2D localToAncestor(Point2D point, Node local, Node ancestor) {
		if (local == ancestor) {
			return point;
		}

		Point2D p = local.localToParent(point);
		return localToAncestor(p, local.getParent(), ancestor);
	}

	public static Bounds localToAncestor(Bounds bounds, Node local, Node ancestor) {
		if (local == ancestor || local == null) {
			return bounds;
		}

		Bounds b = local.localToParent(bounds);
		return localToAncestor(b, local.getParent(), ancestor);
	}

	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		_listenerList.remove(listener);
	}

	/**
	 * If the given node is selected, remove it from the selection.
	 * 
	 * @param node
	 */
	public void removeNodeFromSelection(Node node) {
		if (isSelected(node)) {
			@SuppressWarnings("unchecked")
			List<Object> list = new ArrayList<>(_selection.toList());
			list.remove(node);
			setSelection(new StructuredSelection(list));
		}
	}

	public void abort() {
		if (_selectedNodes.isEmpty()) {
			return;
		}

		setSelection(StructuredSelection.EMPTY);
	}

	public void selectAll() {
		_canvas.getSelectionBehavior().setSelection(new StructuredSelection(_canvas.getWorldNode().getChildren()));
	}
}
