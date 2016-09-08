package org.eclipse.jetty.load.generator.profile;

/**
 *
 */
public class ResourceGroup
{

    private Step step;

    public ResourceGroup()
    {
        // no op
    }

    public ResourceGroup( Step step )
    {
        this.step = step;
    }

    public Step getStep()
    {
        return step;
    }

    @Override
    public String toString()
    {
        return "ResourceGroup{" + "step=" + step + '}';
    }

    public static class Builder
    {
        private Step step;

        public Builder()
        {
            this.step = new Step();
            this.step.setWait( true );
        }

        public ResourceGroup build()
        {
            return new ResourceGroup( this.step );
        }

        public Builder resource( String path )
        {
            this.step.addResource( new Resource( path ) );
            return this;
        }

        public Builder then()
        {
            this.step.setWait( true );
            return this;
        }

        public Builder resourceGroup( ResourceGroup resourceGroup )
        {
            this.step.addStep( resourceGroup.getStep() );
            return this;
        }

        public Builder size( int size )
        {
            this.step.getResources().get( this.step.getResources().size() - 1 ).size( size );
            return this;
        }

        public Builder timeout( long timeout )
        {
            this.step.setTimeout( timeout );
            return this;
        }


        public Builder method( String method )
        {
            this.step.getResources().get( this.step.getResources().size() - 1 ).method( method );
            return this;
        }

    }

}
