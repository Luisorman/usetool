/*
 * USE - UML based specification environment
 * Copyright (C) 1999-2004 Mark Richters, University of Bremen
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

// $Id$

package org.tzi.use.gui.views.diagrams;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import org.tzi.use.graph.DirectedGraph;
import org.tzi.use.gui.graphlayout.SpringLayout;
import org.tzi.use.gui.util.Selection;
import org.tzi.use.gui.views.diagrams.event.ActionLoadLayout;
import org.tzi.use.gui.views.diagrams.event.ActionSaveLayout;
import org.tzi.use.gui.views.diagrams.event.ActionSelectAll;
import org.tzi.use.gui.views.diagrams.event.HideAdministration;

/**
 * Combines everything that the class and object diagram have in common.
 * 
 * @version $ProjectVersion: 0.393 $
 * @author Fabian Gutsche
 */
@SuppressWarnings("serial")
public abstract class DiagramView extends JPanel 
                                  implements Printable {
    protected DirectedGraph<NodeBase, EdgeBase> fGraph;
    protected PrintWriter fLog;
    protected Selection fNodeSelection;
    protected Selection fEdgeSelection;
    
    // needed for autolayout
    protected LayoutThread fLayoutThread;
    protected volatile SpringLayout<NodeBase> fLayouter;
    protected final Object fLock = new Object();

    protected Set<Object> fHiddenNodes;
    protected Set<Object> fHiddenEdges;
    protected HideAdministration fHideAdmin; // coordinates the hiding of nodes
    protected ActionLoadLayout fActionLoadLayout;
    protected ActionSaveLayout fActionSaveLayout;
    protected ActionSelectAll fActionSelectAll;
    
    protected DiagramOptions fOpt;
    
    protected LayoutInfos fLayoutInfos;

    /**
	 * This value is read from the system properties file.
	 * It determines the minimum width of a class node 
	 */
	protected int minClassNodeWidth;
	
	/**
	 * This value is read from the system properties file.
	 * It determines the minimum height of a class node 
	 */
	protected int minClassNodeHeight;
	
	public DiagramView() {
		minClassNodeHeight = Integer.parseInt(System.getProperty("use.gui.view.classdiagram.class.minheight"));
        minClassNodeWidth = Integer.parseInt(System.getProperty("use.gui.view.classdiagram.class.minwidth"));
	}
	
    /**
     * Determinds if the auto layout of the diagram is on or off.
     * @return <code>true</code> if the auto layout is on, otherwise
     * <code>false</code>
     */
    public abstract boolean isDoAutoLayout();
    
    /**
     * Deletes all hidden elements form this diagram.
     */
    public abstract void deleteHiddenElementsFromDiagram( Set<Object> nodesToHide, 
                                                          Set<Object> edgesToHide );
    
    /**
     * Determines if the popup menu of this diagram should be shown.
     * @param e MouseEvent.
     * @return <code>true</code> if the popup menu should be shown,
     * otherwise <code>false</code>.
     */
    public abstract boolean maybeShowPopup( MouseEvent e );
    
    /**
     * When the content of the diagram changes,e.g., after hiding or showing
     * a node, additional calculations are needed, which are not required
     * when "just" redrawing.
     */
    private boolean isDiagramContentChanged = true;

    /**
     * When the content of the diagram changes,e.g., after hiding or showing
     * a node, additional calculations are needed, which are not required
     * when "just" redrawing.
     */
    public void invalidateContent() {
    	this.isDiagramContentChanged = true;
    	this.repaint();
    }
    
    /**
     * Draws the diagram.
     * @param g The diagram is drawn into this Graphics object.
     */
    public synchronized void drawDiagram(Graphics g) {
    	Graphics2D g2d = (Graphics2D)g;
    	
    	if ( fOpt.isDoAntiAliasing() )
    		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                 RenderingHints.VALUE_ANTIALIAS_ON);

        Dimension d = getSize();
        g2d.setColor(getBackground());
        g2d.fillRect(0, 0, d.width, d.height);
 
        Iterator<EdgeBase> edgeIterator;
        // Calculates the min height of classifiers
        if (isDiagramContentChanged) {
	        edgeIterator = fGraph.edgeIterator();
	        while (edgeIterator.hasNext()) {
	        	EdgeBase e = edgeIterator.next();
	        	e.onFirstDraw(g2d);
	        }
        }
        
        // set rectangle size for nodes
        // It is important to do this first, otherwise rolenames will
        // not be dynamically moved after displaying attributes! And
        // after hiding an object which is participating in a two link
        // association, the two links won't be displayed in the right
        // way.
        Iterator<NodeBase> nodeIterator = fGraph.iterator();
        double heightHint;
        while (nodeIterator.hasNext()) {
            NodeBase n = nodeIterator.next();
            
            // Set min height wrt. displayed qualifiers

            // These variables are used to set the offset only when more then
            // one special end, e.g., a qualifier are present.
            // Otherwise they should be centered (offset == 0)
            EdgeBase firstSpecial = null;
            int specialCounter = 0;
            boolean firstWasSource = true;
                        
            if (isDiagramContentChanged) {
            	n.setMinHeight(minClassNodeHeight);
            	int minHeight = 0;
            	Set<EdgeBase> myEdges = fGraph.allEdges(n);
            	for (EdgeBase e : myEdges) {
            		heightHint = 0;
            		if (e.source().equals(n) && e.hasSpecialSource()) {
            			if (specialCounter > 0) {
            				e.setSpecialSourceYOffSet(minHeight + 4);
            			} else {
            				e.setSpecialSourceYOffSet(0);
            				firstSpecial = e;
            				firstWasSource = true;
            			}
            			
            			heightHint = e.getSourceHeightHint();
            			++specialCounter;
            		}
            		if (e.target().equals(n) && e.hasSpecialTarget()) {
            			if (specialCounter > 0) {
            				e.setSpecialTargetYOffSet(minHeight + 4);
            			} else {
            				firstSpecial = e;
            				firstWasSource = false;
            			}
            			
            			heightHint = e.getTargetHeightHint();
            			++specialCounter;
            		}
            		
            		if (specialCounter == 2) {
            			if (firstWasSource) {
            				firstSpecial.setSpecialSourceYOffSet(4);
            			} else {
            				firstSpecial.setSpecialTargetYOffSet(4);
            			}
            		}
            		
            		if (heightHint > 0)
        				minHeight += heightHint + 4;
            	}
            	n.setMinHeight(Math.max(minHeight + 4, n.getMinHeight()));
            }
            n.setRectangleSize(g2d);
        }

        
        // draw edges
        // they need to be drawn first otherwise the association will
        // be drawn above the nodes
        edgeIterator = fGraph.edgeIterator();
        Set<EdgeBase> drawnEdges = new HashSet<EdgeBase>();
        Set<EdgeBase> edges = new HashSet<EdgeBase>();
        
        while ( edgeIterator.hasNext() ) {
            EdgeBase e = edgeIterator.next();
            if ( !drawnEdges.contains( e ) ) {
                edges = e.checkForNewPositionAndDraw( fGraph, g2d);
                if ( edges != null ) {
                    drawnEdges.addAll( edges );
                }
            }
        }

        // draw nodes
        nodeIterator = fGraph.iterator();
        while (nodeIterator.hasNext()) {
            NodeBase n = (NodeBase) nodeIterator.next();
            n.draw(g2d);
        }
        
        isDiagramContentChanged = false;
    }
        
    /**
     * Returns the options of a specific diagram.
     */
    public DiagramOptions getOptions() {
        return fOpt;
    }
    
    /**
     * Returns the log panel.
     */
    public PrintWriter getLog() {
        return fLog;
    }
    
    /**
     * All currently hidden nodes in this view
     * @return
     */
    public Set<Object> getHiddenNodes() {
    	return this.fHiddenNodes;
    }
    
    /**
     * All currently hidden edges in this view
     * @return
     */
    public Set<Object> getHiddenEdges() {
    	return this.fHiddenEdges;
    }
    
    /**
     * The graph of the diagram
     * @return
     */
    public DirectedGraph<NodeBase, EdgeBase> getGraph() {
    	return this.fGraph;
    }
    
    /**
     * The hide administration of this diagram
     * @return
     */
    public HideAdministration getHideAdmin() {
    	return this.fHideAdmin;
    }
    
    /**
     * Prints the diagram. Implementation of Printable interface.
     */
    public int print(Graphics g, PageFormat pf, int pi) throws PrinterException {
        if (pi >= 1)
            return Printable.NO_SUCH_PAGE;
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.translate(pf.getImageableX(), pf.getImageableY());
        g2d.translate(pf.getImageableWidth() / 2, pf.getImageableHeight() / 2);
        
        Dimension d = getSize();
        double scale = Math.min(pf.getImageableWidth() / d.width, pf
                                .getImageableHeight()
                                / d.height);
        // fit to page
        if (scale < 1.0)
            g2d.scale(scale, scale);
        g2d.translate(-d.width / 2.0, -d.height / 2.0);
        
        Font f = Font.getFont("use.gui.print.diagramFont", getFont());
        g2d.setFont(f);
        
        drawDiagram(g2d);
        return Printable.PAGE_EXISTS;
    }
    
    /**
     * Prints the diagram.
     * @param pf
     * @param jobname
     */
    public void printDiagram( PageFormat pf, String jobname ) {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName( jobname );
        job.setPrintable( this, pf );

        if ( job.printDialog() ) {
            // Print the job if the user didn't cancel printing
            try {
                job.print();
            } catch ( Exception ex ) {
                JOptionPane.showMessageDialog( this, ex.getMessage(), "Error",
                                               JOptionPane.ERROR_MESSAGE );
            }
        }
    }
    
    /**
     * Trys to find an edge on the given coordinates and adds a node 
     * on this edge if the mouse clickcount is 2.
     * @param x X-coordinate.
     * @param y Y-coordinate.
     * @param clickCount The mouse click count.
     */
    public void findEdge( int x, int y, int clickCount ) {
        Iterator<EdgeBase> it = fGraph.edgeIterator();
        
        while ( it.hasNext() ) {
            EdgeBase e = it.next();
            e.occupiesThanAdd( x, y, clickCount );
            repaint();
        }
    }
    
    /**
     * Finds node occupying the given position.
     * 
     * @return null if no such node could be found.
     */
    public PlaceableNode findNode(int x, int y) {
        PlaceableNode res = null;
        Iterator<NodeBase> nIter = fGraph.iterator();

        while (nIter.hasNext()) {
            NodeBase n = nIter.next();
            
            if ( n instanceof DiamondNode ) {
                EdgeProperty an = ((DiamondNode) n).getAssocName();
                if ( an != null && an.occupies( x, y ) ) {
                    res = an;
                }
            }
            if (n.occupies(x, y)) {
                // Do not break here. We search in the same order
                // which is used for drawing. There may be another
                // node drawn on top of this node. That node should be
                // picked.
                res = n;
            }
        }
        
        if ( res == null ) {
            Iterator<EdgeBase> eIter = fGraph.edgeIterator();
            
            while ( eIter.hasNext() ) {
                EdgeBase e = eIter.next();
                res = e.getWayPoint(x, y);
                
                if (res != null) break;
            }
        }      
        return res;
    }
    
    
    /**
     * Returns a unified JPopUpMenu of the class- and objectdiagram.
     */
    protected JPopupMenu unionOfPopUpMenu() {
        // context menu on right mouse click
        JPopupMenu popupMenu = new JPopupMenu();

        
        final JCheckBoxMenuItem cbAutoLayout = new JCheckBoxMenuItem(
        "Auto-Layout");
        cbAutoLayout.setState( fOpt.isDoAutoLayout() );
        cbAutoLayout.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent ev) {
                fOpt.setDoAutoLayout( ev.getStateChange() == ItemEvent.SELECTED );
            }
        });
        final JCheckBoxMenuItem cbAssocNames = new JCheckBoxMenuItem(
        "Show association names");
        cbAssocNames.setState( fOpt.isShowAssocNames() );
        cbAssocNames.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent ev) {
                fOpt.setShowAssocNames( ev.getStateChange() == ItemEvent.SELECTED );
                repaint();
            }
        });
        final JCheckBoxMenuItem cbRolenames = new JCheckBoxMenuItem(
        "Show role names");
        cbRolenames.setState( fOpt.isShowRolenames() );
        cbRolenames.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent ev) {
                fOpt.setShowRolenames( ev.getStateChange() == ItemEvent.SELECTED );
                repaint();
            }
        });
        final JCheckBoxMenuItem cbAttrValues = new JCheckBoxMenuItem(
        "Show attributes"); // values");
        cbAttrValues.setState( fOpt.isShowAttributes() );
        cbAttrValues.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent ev) {
                fOpt.setShowAttributes( ev.getStateChange() == ItemEvent.SELECTED );
                repaint();
            }
        });
        
        final JCheckBoxMenuItem cbAntiAliasing = new JCheckBoxMenuItem(
        "Anti-aliasing");
        cbAntiAliasing.setState( fOpt.isDoAntiAliasing() );
        cbAntiAliasing.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent ev) {
                fOpt.setDoAntiAliasing( ev.getStateChange() == ItemEvent.SELECTED );
                repaint();
            }
        });
        
        popupMenu.add(cbAssocNames);
        popupMenu.add(cbRolenames);
        popupMenu.add(cbAttrValues);
        popupMenu.addSeparator();
        popupMenu.add(cbAutoLayout);
        popupMenu.add(cbAntiAliasing);
        
        if (fGraph.size() > 0) {
            popupMenu.addSeparator();
            popupMenu.add(fActionLoadLayout);
            popupMenu.add(fActionSaveLayout);
        }

        return popupMenu;
    }
    
    
    /**
     * Stops the auto layout thread.
     */
    public void stopLayoutThread() {
        fLayoutThread = null;
    }

    /**
     * Starts the auto layout thread.
     */
    public void startLayoutThread() {
        fLayoutThread = new LayoutThread();
        fLayoutThread.start();
    }

    /**
     * This is called by the LayoutThread to generate a new layout. The layouter
     * object can be reused as long as the graph and the size of the drawing
     * area does not change.
     */  
    private synchronized void autoLayout() {
        if (fLayouter == null) {
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0)
                return;
            fLayouter = new SpringLayout<NodeBase>(fGraph, w, h, 80, 20);
            fLayouter.setEdgeLen(150);
        }
        fLayouter.layout();
        repaint();
    }
    
    class LayoutThread extends Thread {
        public void run() {
            Thread me = Thread.currentThread();
            while ( fLayoutThread == me ) {
                if ( isDoAutoLayout() ) {
                    synchronized ( fLock ) {
                        autoLayout();
                    }
                }
                try {
                    Thread.sleep( 50 );
                } catch ( InterruptedException e ) {
                    break;
                }
            }
        }
    }

}
