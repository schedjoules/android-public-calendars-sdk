package org.dmfs.webcal.utils.color;

/**
 * Base class for {@link Color}s that simply delegate to another {@link Color}.
 *
 * @author Gabor Keszthelyi
 */
// TODO Remove when available from dmfs android tools library
public abstract class DelegatingColor implements Color
{
    private final Color mDelegate;


    public DelegatingColor(Color delegate)
    {
        mDelegate = delegate;
    }


    @Override
    public final int argb()
    {
        return mDelegate.argb();
    }
}
